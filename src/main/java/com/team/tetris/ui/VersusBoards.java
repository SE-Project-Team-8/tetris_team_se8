/*
 * 대전 화면의 보드 영역: 두 보드(바깥)·플레이어 정보(안쪽)·VS와 공격 연출. 로컬 대전과 P2P 대전 화면이 같이 쓴다.
 */
package com.team.tetris.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.swing.JComponent;
import javax.swing.Timer;

import com.team.tetris.block.ColorScheme;
import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.ui.theme.CellPainter;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/**
 * 두 플레이어의 보드·NEXT·SCORE·LEVEL·공격 대기열과 가운데 VS를 그리고, 공격 연출을 돌린다.
 * 화면은 플레이어마다 {@link #side(Player)}에 보드·점수 등을 채우고, 대기열이 바뀌면 {@link #queueChanged}를 부른다.
 *
 * 공격 연출 (design-spec §3.2, 컴포넌트 시트 "공격 이펙트", VersusAttack 목업):
 * ① 공격 발생: 공격자 보드 테두리가 플레이어색으로 한 번 글로우 펄스.
 * ② 발사: 회색 뭉치(>_<)가 꼬리 잔상 3개를 달고 공격자가 지운 줄에서 상대 대기열로 날아감.
 * ③ 적재: 상대 대기열의 새 줄이 노랑으로 2회 깜빡인 뒤 회색으로 확정.
 * ④ 삽입: 대기열이 보드에 들어갈 때 회색 뭉치가 대기열에서 보드 맨 아래로 대각선 낙하, 삽입 구역은 accent 점선 + 7% 틴트.
 * 연출이 있는 동안에만 타이머가 돈다(상시 루프 없음). 모든 메서드는 EDT에서 부른다.
 */
final class VersusBoards {
    private static final float BASE_WIDTH = 1280f;
    private static final int BASE_FRAME_PADDING = 10;
    private static final int BASE_GAP = 16;
    private static final int BASE_INFO_WIDTH = 150;
    private static final int BASE_VS_SPACE = 150;
    private static final int ATTACK_TICK_MS = 16;
    /** ① 공격자 보드 글로우 펄스. */
    private static final int GLOW_MS = 320;
    /** ②·④ 회색 뭉치가 날아가는 시간 (컴포넌트 시트: 약 300ms). */
    private static final int COMET_MS = 320;
    /** ③ 대기열 새 줄 노랑 깜빡임 2회 (한 번 켜짐·꺼짐 120ms씩). */
    private static final int QUEUE_FLASH_MS = 480;
    /** ④ 삽입 구역 하이라이트. */
    private static final int INSERT_ZONE_MS = 560;
    /** 꼬리 잔상 불투명도 (컴포넌트 시트: 50→12%). */
    private static final float[] TRAIL_ALPHAS = {0.50f, 0.28f, 0.12f};
    // 정보 열 세로 배치 (L 기준 px)
    private static final int INFO_LABEL = 30;
    private static final int INFO_NEXT = 86;
    private static final int INFO_STAT = 78;
    private static final int INFO_GAP = 12;
    private static final int QUEUE_LABEL = 32;
    private static final int QUEUE_PADDING = 14;

    private enum EffectKind { GLOW, SEND, QUEUE_FLASH, INSERT }


    /**
     * 공격 연출 하나.
     *
     * @param player GLOW는 공격자, 나머지는 공격을 받은(대기열·보드) 플레이어
     * @param rows 줄 수
     * @param fromIndex QUEUE_FLASH에서 깜빡일 대기열 줄 범위의 시작(포함)
     * @param fromRow SEND가 출발하는 공격자 보드의 행
     */
    private record AttackEffect(EffectKind kind, Player player, int rows, int fromIndex, float fromRow,
                                long startMs, int durationMs) {
        float progress(long now) {
            return Math.max(0f, Math.min(1f, (float) (now - startMs) / durationMs));
        }

        boolean started(long now) {
            return now >= startMs;
        }

        boolean finished(long now) {
            return now >= startMs + durationMs;
        }
    }


    /** 한 번 그릴 때 정한 배치. 연출 위치 계산과 플레이어 영역 다시 그리기에 쓴다. */
    private record Layout(float scale, int pitch, int framePadding, int frameY, int frameWidth, int frameHeight,
                          Map<Player, Integer> frameX, Map<Player, Integer> infoX, int infoWidth,
                          Map<Player, Rectangle> queueGrid, int vsCenterX, int vsCenterY) {
        int boardX(Player player) {
            return frameX.get(player) + framePadding;
        }

        int boardY() {
            return frameY + framePadding;
        }

        Rectangle region(Player player) {
            int left = Math.min(frameX.get(player), infoX.get(player));
            int right = Math.max(frameX.get(player) + frameWidth, infoX.get(player) + infoWidth);
            int glow = Math.round(16 * scale);
            return new Rectangle(left - glow, frameY - glow, right - left + glow * 2, frameHeight + glow * 2);
        }
    }


    private void addEffect(AttackEffect effect) {
        effects.add(effect);
        if (!attackTimer.isRunning()) attackTimer.start();
        host.repaint();
    }


    private void advanceEffects() {
        long now = now();
        effects.removeIf(effect -> effect.finished(now));
        if (effects.isEmpty()) attackTimer.stop();
        host.repaint();
    }


    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }


    /** 대기열이 바뀐 순간: 늘었으면 공격이 온 것(①②③), 비었으면 보드에 들어간 것(④). */
    void queueChanged(Player player, List<GarbageLine> lines) {
        Side side = sides.get(player);
        int before = side.queue.size();
        side.queue = List.copyOf(lines);
        int after = side.queue.size();
        long now = now();
        if (after > before) {
            Player attacker = player.opponent();
            addEffect(new AttackEffect(EffectKind.GLOW, attacker, after - before, 0, 0f, now, GLOW_MS));
            addEffect(new AttackEffect(EffectKind.SEND, player, after - before, 0,
                    sides.get(attacker).lastClearedRow, now, COMET_MS));
            // 회색 뭉치가 대기열에 닿는 순간부터 새 줄이 깜빡인다. 그 전에는 새 줄을 비워 둔다.
            addEffect(new AttackEffect(EffectKind.QUEUE_FLASH, player, after - before, before, 0f,
                    now + COMET_MS, QUEUE_FLASH_MS));
        } else if (after == 0 && before > 0) {
            addEffect(new AttackEffect(EffectKind.INSERT, player, before, 0, 0f, now, INSERT_ZONE_MS));
        }
        host.repaint();
    }


    private Layout computeLayout(int width, int height) {
        float scale = Math.max(0.5f, width / BASE_WIDTH);
        int header = Math.round(VersusScreen.BASE_HEADER * scale);
        int footer = Math.round(VersusScreen.BASE_FOOTER * scale);
        int padding = Math.round(BASE_FRAME_PADDING * scale);
        int gap = Math.round(BASE_GAP * scale);
        int infoWidth = Math.round(BASE_INFO_WIDTH * scale);
        int vsSpace = Math.round(BASE_VS_SPACE * scale);
        int margin = Math.round(32 * scale);
        int pitch = Math.max(8, Math.min(
                (height - header - footer - padding * 2) / GameConstants.BOARD_ROWS,
                ((width - margin * 2 - gap * 2 - infoWidth * 2 - vsSpace) / 2 - padding * 2) / GameConstants.BOARD_COLS));
        int frameWidth = pitch * GameConstants.BOARD_COLS + padding * 2;
        int frameHeight = pitch * GameConstants.BOARD_ROWS + padding * 2;
        int total = frameWidth * 2 + gap * 2 + infoWidth * 2 + vsSpace;
        int left = (width - total) / 2;
        Map<Player, Integer> frameX = new EnumMap<>(Player.class);
        Map<Player, Integer> infoX = new EnumMap<>(Player.class);
        frameX.put(Player.P1, left);
        infoX.put(Player.P1, left + frameWidth + gap);
        infoX.put(Player.P2, left + frameWidth + gap + infoWidth + vsSpace);
        frameX.put(Player.P2, left + total - frameWidth);
        int frameY = header;
        Map<Player, Rectangle> queueGrid = new EnumMap<>(Player.class);
        for (Player player : Player.values()) queueGrid.put(player, queueGridBounds(infoX.get(player), frameY, infoWidth, scale));
        return new Layout(scale, pitch, padding, frameY, frameWidth, frameHeight, frameX, infoX, infoWidth, queueGrid,
                width / 2, frameY + frameHeight / 2);
    }


    private static int queuePanelTop(int frameY, float scale) {
        return frameY + Math.round((INFO_LABEL + INFO_NEXT + INFO_STAT * 2 + INFO_GAP * 3) * scale);
    }


    private void drawFrameAndBoard(Graphics2D g2d, Player player, long now) {
        Side side = sides.get(player);
        float scale = layout.scale();
        int x = layout.frameX().get(player);
        int y = layout.frameY();
        int radius = Math.round(16 * scale);
        Color color = VersusScreen.playerColor(player);
        // ① 공격 발생: 공격자 보드 테두리를 플레이어색으로 한 번 글로우 펄스 (테두리 +3px, 바깥 빛 번짐).
        for (AttackEffect effect : effects) {
            if (effect.kind() != EffectKind.GLOW || effect.player() != player || !effect.started(now)) continue;
            float strength = 1f - effect.progress(now);
            for (int ring = 3; ring >= 1; ring--) {
                int spread = Math.round(ring * 5 * scale);
                UiPainter.strokeRoundRect(g2d, x - spread, y - spread, layout.frameWidth() + spread * 2,
                        layout.frameHeight() + spread * 2, radius + spread,
                        UiColors.withAlpha(color, 0.22f * strength * (4 - ring) / 3f), Math.max(2f, 4 * scale));
            }
        }
        float border = 3f + 3f * glowStrength(player, now);
        int shake = side.board.shakeOffset(scale);
        Graphics2D board = (Graphics2D) g2d.create();
        try {
            board.translate(0, shake);
            UiPainter.drawPanel(board, x, y, layout.frameWidth(), layout.frameHeight(), radius,
                    UiColors.BOARD, color, border);
            side.board.paintBoard(board, layout.boardX(player), layout.boardY(), layout.pitch());
        } finally {
            board.dispose();
        }
        int boardWidth = layout.pitch() * GameConstants.BOARD_COLS;
        int boardHeight = layout.pitch() * GameConstants.BOARD_ROWS;
        side.board.paintToast(g2d, layout.boardX(player), layout.boardY(), boardWidth, boardHeight, scale);
    }


    private float glowStrength(Player player, long now) {
        float strength = 0f;
        for (AttackEffect effect : effects) {
            if (effect.kind() == EffectKind.GLOW && effect.player() == player && effect.started(now)) {
                strength = Math.max(strength, 1f - effect.progress(now));
            }
        }
        return strength;
    }


    private void drawInfoColumn(Graphics2D g2d, Player player, long now) {
        Side side = sides.get(player);
        float scale = layout.scale();
        int x = layout.infoX().get(player);
        int width = layout.infoWidth();
        int y = layout.frameY();
        int gap = Math.round(INFO_GAP * scale);
        int radius = Math.round(12 * scale);
        Color color = VersusScreen.playerColor(player);

        // 플레이어 이름: P1은 왼쪽, P2는 오른쪽 정렬 (보드 쪽).
        g2d.setFont(UiFonts.display(Math.round(22 * scale)));
        g2d.setColor(color);
        FontMetrics nameMetrics = g2d.getFontMetrics();
        String name = label(player);
        int nameX = player == Player.P1 ? x : x + width - nameMetrics.stringWidth(name);
        g2d.drawString(name, nameX, y + Math.round(20 * scale));
        y += Math.round(INFO_LABEL * scale);

        int nextHeight = Math.round(INFO_NEXT * scale);
        UiPainter.drawPanel(g2d, x, y, width, nextHeight, radius);
        drawPanelLabel(g2d, "NEXT", x, y, width, scale);
        if (side.next.length > 0) {
            int labelSpace = Math.round(28 * scale);
            side.board.paintPreview(g2d, side.next[0], x, y + labelSpace, width, nextHeight - labelSpace,
                    Math.round(18 * scale));
        }
        y += nextHeight + gap;

        int statHeight = Math.round(INFO_STAT * scale);
        drawStat(g2d, "SCORE", String.format(Locale.US, "%,d", side.score), UiColors.CREAM, x, y, width, statHeight, scale);
        y += statHeight + gap;
        drawStat(g2d, "LEVEL", String.valueOf(side.level), UiColors.LEVEL, x, y, width, statHeight, scale);
        y += statHeight + gap;

        drawQueuePanel(g2d, player, x, y, width, scale, now);
    }


    private static void drawStat(Graphics2D g2d, String label, String value, Color valueColor,
                                 int x, int y, int width, int height, float scale) {
        UiPainter.drawPanel(g2d, x, y, width, height, Math.round(12 * scale));
        drawPanelLabel(g2d, label, x, y, width, scale);
        g2d.setFont(UiFonts.mono(Math.round(26 * scale)));
        g2d.setColor(valueColor);
        UiPainter.drawCentered(g2d, value, x + width / 2, y + height - Math.round(16 * scale));
    }


    private static void drawPanelLabel(Graphics2D g2d, String label, int x, int y, int width, float scale) {
        g2d.setFont(UiFonts.body(Math.round(11 * scale)).deriveFont(Map.of(TextAttribute.TRACKING, 0.12f)));
        g2d.setColor(UiColors.TEXT_MUTED);
        UiPainter.drawCentered(g2d, label, x + width / 2, y + Math.round(22 * scale));
    }


    /**
     * 공격 대기열: 10줄×10칸 격자에 대기 중인 줄을 빈 칸이 보이는 모양 그대로 회색으로 그린다 (위가 먼저 온 줄).
     * 줄이 있으면 패널 테두리와 제목을 플레이어색으로 바꿔 "다음 블럭 고정 때 들어온다"는 것을 알린다.
     */
    private void drawQueuePanel(Graphics2D g2d, Player player, int x, int y, int width, float scale, long now) {
        Side side = sides.get(player);
        Rectangle grid = layout.queueGrid().get(player);
        int padding = Math.round(QUEUE_PADDING * scale);
        int height = grid.y + grid.height + padding - y;
        boolean waiting = !side.queue.isEmpty();
        Color color = VersusScreen.playerColor(player);
        UiPainter.drawPanel(g2d, x, y, width, height, Math.round(12 * scale), UiColors.PANEL,
                waiting ? color : UiColors.PANEL_BORDER, waiting ? 2.5f : 1f);
        g2d.setFont(UiFonts.body(Math.round(12 * scale)));
        g2d.setColor(waiting ? color : UiColors.TEXT_MUTED);
        UiPainter.drawCentered(g2d, "공격 대기열 " + side.queue.size() + "/" + GameConstants.ATTACK_QUEUE_MAX_LINES,
                x + width / 2, y + Math.round(22 * scale));

        int cell = grid.width / GarbageLine.COLS;
        int inset = Math.max(1, Math.round(cell * 0.1f));
        int arc = Math.max(2, Math.round(cell * 0.35f));
        for (int row = 0; row < GameConstants.ATTACK_QUEUE_MAX_LINES; row++) {
            GarbageLine line = row < side.queue.size() ? side.queue.get(row) : null;
            Color lineColor = UiColors.GARBAGE;
            if (line != null) {
                AttackEffect flash = flashFor(player, row);
                if (flash != null && !flash.started(now)) {
                    line = null;   // 회색 뭉치가 아직 날아오는 중
                } else if (flash != null && !flash.finished(now)) {
                    long phase = (now - flash.startMs()) / (QUEUE_FLASH_MS / 4);
                    if (phase % 2 == 0) lineColor = UiColors.ACCENT;
                }
            }
            for (int col = 0; col < GarbageLine.COLS; col++) {
                int cellX = grid.x + col * cell + inset;
                int cellY = grid.y + row * cell + inset;
                int size = cell - inset * 2;
                if (line != null && line.filled(col)) {
                    g2d.setColor(lineColor);
                } else {
                    g2d.setColor(UiColors.withAlpha(UiColors.BOARD, 0.9f));
                }
                g2d.fillRoundRect(cellX, cellY, size, size, arc, arc);
            }
        }
    }


    private AttackEffect flashFor(Player player, int row) {
        for (AttackEffect effect : effects) {
            if (effect.kind() == EffectKind.QUEUE_FLASH && effect.player() == player
                    && row >= effect.fromIndex() && row < effect.fromIndex() + effect.rows()) {
                return effect;
            }
        }
        return null;
    }


    private void drawVs(Graphics2D g2d, float scale) {
        int radius = Math.round(62 * scale);
        int cx = layout.vsCenterX();
        int cy = layout.vsCenterY();
        g2d.setColor(UiColors.darker(UiColors.PANEL, 0.35f));
        g2d.fillOval(cx - radius, cy - radius + Math.round(5 * scale), radius * 2, radius * 2);
        g2d.setColor(UiColors.PANEL);
        g2d.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g2d.setFont(UiFonts.display(Math.round(54 * scale)));
        FontMetrics metrics = g2d.getFontMetrics();
        int baseline = UiPainter.centeredBaseline(metrics, cy - radius, radius * 2);
        g2d.setColor(UiColors.darker(UiColors.ACCENT, 0.5f));
        UiPainter.drawCentered(g2d, "VS", cx + Math.round(2 * scale), baseline + Math.round(3 * scale));
        g2d.setColor(UiColors.ACCENT);
        UiPainter.drawCentered(g2d, "VS", cx, baseline);
    }


    private void drawAttackEffects(Graphics2D g2d, long now) {
        for (AttackEffect effect : effects) {
            if (!effect.started(now)) continue;
            switch (effect.kind()) {
                case SEND -> drawComet(g2d, sendStart(effect), queueCenter(effect.player()), effect, now, null);
                case INSERT -> {
                    drawInsertZone(g2d, effect, now);
                    drawComet(g2d, queueCenter(effect.player()), insertTarget(effect), effect, now,
                            effect.rows() + "줄 공격 삽입!");
                }
                default -> { }
            }
        }
    }


    private float[] sendStart(AttackEffect effect) {
        Player attacker = effect.player().opponent();
        int pitch = layout.pitch();
        return new float[]{layout.boardX(attacker) + pitch * GameConstants.BOARD_COLS / 2f,
                layout.boardY() + (effect.fromRow() + 0.5f) * pitch};
    }


    private float[] queueCenter(Player player) {
        Rectangle grid = layout.queueGrid().get(player);
        return new float[]{(float) grid.getCenterX(), (float) grid.getCenterY()};
    }


    private float[] insertTarget(AttackEffect effect) {
        int pitch = layout.pitch();
        return new float[]{layout.boardX(effect.player()) + pitch * GameConstants.BOARD_COLS / 2f,
                layout.boardY() + (GameConstants.BOARD_ROWS - effect.rows() / 2f) * pitch};
    }


    /**
     * 회색 뭉치(>_< 공격줄 칸)가 꼬리 잔상 3개를 달고 {@code from}에서 {@code to}로 날아간다.
     * 도착하면 사라진다. 삽입(④)은 점점 빨라지며 떨어지게 한다.
     */
    private void drawComet(Graphics2D g2d, float[] from, float[] to, AttackEffect effect, long now, String label) {
        float travel = Math.min(1f, (float) (now - effect.startMs()) / COMET_MS);
        if (travel >= 1f) return;
        boolean falling = effect.kind() == EffectKind.INSERT;
        int size = Math.round(layout.pitch() * 1.05f);
        Graphics2D comet = (Graphics2D) g2d.create();
        try {
            for (int step = TRAIL_ALPHAS.length; step >= 0; step--) {
                float t = travel - step * 0.09f;
                if (t < 0f) continue;
                float eased = falling ? t * t : 1f - (1f - t) * (1f - t);
                float px = from[0] + (to[0] - from[0]) * eased;
                float py = from[1] + (to[1] - from[1]) * eased;
                if (step == 0) {
                    comet.setComposite(AlphaComposite.SrcOver);
                    CellPainter.drawGarbage(comet, Math.round(px - size / 2f), Math.round(py - size / 2f), size);
                    if (label != null) drawCometLabel(comet, label, px, py, size, from[0] > to[0]);
                } else {
                    int ghost = Math.round(size * (1f - step * 0.16f));
                    comet.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TRAIL_ALPHAS[step - 1]));
                    comet.setColor(UiColors.GARBAGE);
                    int arc = CellPainter.radius(ghost) * 2;
                    comet.fillRoundRect(Math.round(px - ghost / 2f), Math.round(py - ghost / 2f), ghost, ghost, arc, arc);
                }
            }
        } finally {
            comet.dispose();
        }
    }


    private void drawCometLabel(Graphics2D g2d, String label, float x, float y, int size, boolean labelOnRight) {
        float scale = layout.scale();
        g2d.setFont(UiFonts.display(Math.round(15 * scale)));
        FontMetrics metrics = g2d.getFontMetrics();
        int width = UiPainter.textWidth(metrics, label);
        int gap = Math.round(8 * scale);
        int textX = labelOnRight ? Math.round(x + size / 2f + gap) : Math.round(x - size / 2f - gap - width);
        int baseline = Math.round(y + metrics.getAscent() / 2f - metrics.getDescent() / 2f);
        g2d.setColor(UiColors.darker(UiColors.ACCENT, 0.6f));
        UiPainter.drawText(g2d, label, textX + 1, baseline + 1);
        g2d.setColor(UiColors.ACCENT);
        UiPainter.drawText(g2d, label, textX, baseline);
    }


    /** ④ 삽입 구역: 보드 맨 아래 들어온 줄을 accent 점선 + 7% 틴트로 감싼다. */
    private void drawInsertZone(Graphics2D g2d, AttackEffect effect, long now) {
        float fade = effect.progress(now) < 0.7f ? 1f : (1f - effect.progress(now)) / 0.3f;
        int pitch = layout.pitch();
        int rows = Math.min(effect.rows(), GameConstants.BOARD_ROWS);
        int x = layout.boardX(effect.player());
        int y = layout.boardY() + (GameConstants.BOARD_ROWS - rows) * pitch;
        int width = pitch * GameConstants.BOARD_COLS;
        int height = pitch * rows;
        int arc = CellPainter.radius(pitch) * 2;
        Graphics2D zone = (Graphics2D) g2d.create();
        try {
            zone.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, fade)));
            zone.setColor(UiColors.withAlpha(UiColors.ACCENT, 0.07f));
            zone.fillRoundRect(x, y, width, height, arc, arc);
            float dash = Math.max(3f, pitch * 0.25f);
            zone.setStroke(new BasicStroke(Math.max(2f, 2.5f * layout.scale()), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 10f, new float[]{dash, dash * 0.7f}, 0f));
            zone.setColor(UiColors.ACCENT);
            zone.drawRoundRect(x, y, width, height, arc, arc);
        } finally {
            zone.dispose();
        }
    }


    /** 알림이 온 플레이어 영역만 다시 그린다 (인계서 2단계, 성능 LB-N01). 배치를 아직 모르면 전체를 그린다. */
    void repaintSide(Player player) {
        if (layout == null) {
            host.repaint();
        } else {
            host.repaint(layout.region(player));
        }
    }


    private static Rectangle queueGridBounds(int infoX, int frameY, int infoWidth, float scale) {
        int padding = Math.round(QUEUE_PADDING * scale);
        int cell = (infoWidth - padding * 2) / GarbageLine.COLS;
        int gridWidth = cell * GarbageLine.COLS;
        int x = infoX + (infoWidth - gridWidth) / 2;
        int y = queuePanelTop(frameY, scale) + Math.round(QUEUE_LABEL * scale);
        return new Rectangle(x, y, gridWidth, cell * GameConstants.ATTACK_QUEUE_MAX_LINES);
    }


    /** 플레이어 한 명의 표시 데이터. 화면이 알림을 받아 채운다. */
    static final class Side {
        final BoardView board;
        int score;
        int level = 1;
        int[] next = new int[0];
        List<GarbageLine> queue = List.of();
        /** 마지막으로 지운 줄들의 가운데 행. 공격 회색 뭉치가 여기서 출발한다. */
        float lastClearedRow = GameConstants.BOARD_ROWS - 2;

        Side(BoardView board) {
            this.board = board;
        }

        void reset() {
            board.reset();
            score = 0;
            level = 1;
            next = new int[0];
            queue = List.of();
            lastClearedRow = GameConstants.BOARD_ROWS - 2;
        }

        /** 줄을 지운 행을 기억해 둔다. 이어서 오는 공격의 회색 뭉치가 여기서 출발한다. */
        void linesCleared(List<Integer> rows) {
            if (!rows.isEmpty()) {
                lastClearedRow = (float) rows.stream().mapToInt(Integer::intValue).average().orElse(lastClearedRow);
            }
        }
    }

    private final JComponent host;
    private final Map<Player, Side> sides = new EnumMap<>(Player.class);
    private final Map<Player, String> labels = new EnumMap<>(Player.class);
    private final List<AttackEffect> effects = new ArrayList<>();
    private final Timer attackTimer = new Timer(ATTACK_TICK_MS, event -> advanceEffects());
    private Layout layout;

    /** @param host 다시 그릴 화면 */
    VersusBoards(JComponent host, ItemRegistry items) {
        this.host = Objects.requireNonNull(host, "host");
        Objects.requireNonNull(items, "items");
        for (Player player : Player.values()) {
            sides.put(player, new Side(new BoardView(() -> repaintSide(player), items)));
            labels.put(player, player.name());
        }
    }

    Side side(Player player) {
        return sides.get(player);
    }

    /** 정보 열 위의 플레이어 이름. 기본은 "P1"·"P2". P2P는 "P1 (나)"처럼 내 쪽을 표시한다. */
    void setLabel(Player player, String label) {
        labels.put(player, Objects.requireNonNull(label, "label"));
        host.repaint();
    }

    private String label(Player player) {
        return labels.get(player);
    }

    void setColorScheme(ColorScheme colors) {
        sides.values().forEach(side -> side.board.setColorScheme(colors));
    }

    /** 새 판: 보드·점수·대기열과 진행 중인 연출을 모두 지운다. */
    void reset() {
        effects.clear();
        attackTimer.stop();
        sides.values().forEach(Side::reset);
    }

    /** 화면을 떠날 때 연출 타이머를 멈춘다. */
    void stopEffects() {
        attackTimer.stop();
    }

    /** 이번 크기로 배치를 정한다. 그리기 전에 한 번 부른다. */
    float layout(int width, int height) {
        layout = computeLayout(width, height);
        return layout.scale();
    }

    int vsCenterY() {
        return layout.vsCenterY();
    }

    /** 두 보드·정보 열·VS·공격 연출을 그린다. {@link #layout}을 먼저 부른다. */
    void paint(Graphics2D g2d) {
        long now = now();
        for (Player player : Player.values()) {
            drawFrameAndBoard(g2d, player, now);
            drawInfoColumn(g2d, player, now);
        }
        drawVs(g2d, layout.scale());
        drawAttackEffects(g2d, now);
    }

    /** 마지막으로 그린 배치에서 그 플레이어 공격 대기열 격자의 위치. 아직 그리지 않았으면 지금 크기로 계산한다. */
    Rectangle queueGridBounds(Player player) {
        Layout current = layout != null ? layout : computeLayout(Math.max(1, host.getWidth()), Math.max(1, host.getHeight()));
        return new Rectangle(current.queueGrid().get(player));
    }

}
