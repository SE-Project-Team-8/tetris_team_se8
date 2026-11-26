/*
 * 대전의 앞뒤 화면: 게임 방식 선택, 승자 화면, 시작 메뉴의 대전 모드 항목 (docs/battle/ui-handoff.md 3·4·6단계).
 */
package com.team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.team.tetris.common.Screen;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.game.versus.MatchResult;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.ui.theme.PressFeedback;

class VersusMenusTest {
    @BeforeAll
    static void runMenuPressesImmediately() {
        PressFeedback.setPressDurationForTests(0);
    }

    @AfterAll
    static void restoreMenuPressDuration() {
        PressFeedback.setPressDurationForTests(PressFeedback.PRESS_MS);
    }

    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
    }

    @Test
    void upDownAndEnterStartEachModeAndEscGoesBack() throws Exception {
        List<VersusMode> started = new ArrayList<>();
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                BattleSelectScreen select = new BattleSelectScreen(router, GameSettings::defaults,
                        (mode, seconds) -> started.add(mode));
                router.addScreen(ScreenRouter.MAIN_MENU, stub());
                router.addScreen(ScreenRouter.BATTLE_SELECT, select);
                router.setVisible(true);
                router.showScreen(ScreenRouter.BATTLE_SELECT);
                paint(select);

                select.handleKey(KeyEvent.VK_ENTER);
                select.handleKey(KeyEvent.VK_DOWN);
                select.handleKey(KeyEvent.VK_ENTER);
                select.handleKey(KeyEvent.VK_DOWN);
                select.handleKey(KeyEvent.VK_ENTER);
                select.handleKey(KeyEvent.VK_DOWN);
                assertEquals(VersusMode.NORMAL, select.selectedMode(), "Down wraps around to the first card");
                select.handleKey(KeyEvent.VK_UP);
                assertEquals(VersusMode.TIME_LIMIT, select.selectedMode(), "Up wraps around to the last card");
                assertEquals(List.of(VersusMode.NORMAL, VersusMode.ITEM, VersusMode.TIME_LIMIT), started);

                select.handleKey(KeyEvent.VK_ESCAPE);
                assertEquals(ScreenRouter.MAIN_MENU, router.getCurrentScreenName());
            } finally {
                router.dispose();
            }
        });
    }

    @Test
    void theTimeLimitCardChoosesOneToFiveMinutes() throws Exception {
        List<Integer> seconds = new ArrayList<>();
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                BattleSelectScreen select = new BattleSelectScreen(router, GameSettings::defaults,
                        (mode, limit) -> seconds.add(limit));
                router.addScreen(ScreenRouter.BATTLE_SELECT, select);
                router.setVisible(true);
                router.showScreen(ScreenRouter.BATTLE_SELECT);
                assertEquals(3, select.selectedMinutes(), "Three minutes by default");
                select.handleKey(KeyEvent.VK_RIGHT);
                assertEquals(3, select.selectedMinutes(), "Arrows change the time only on the time-limit card");

                select.handleKey(KeyEvent.VK_UP);   // 일반 → 시간제한 (위로 돌아감)
                assertEquals(VersusMode.TIME_LIMIT, select.selectedMode());
                for (int i = 0; i < 4; i++) select.handleKey(KeyEvent.VK_RIGHT);
                assertEquals(5, select.selectedMinutes(), "Five minutes at most");
                paint(select);
                select.handleKey(KeyEvent.VK_ENTER);
                for (int i = 0; i < 6; i++) select.handleKey(KeyEvent.VK_LEFT);
                assertEquals(1, select.selectedMinutes(), "One minute at least");
                select.handleKey(KeyEvent.VK_ENTER);
                assertEquals(List.of(300, 60), seconds, "The chosen minutes start the match in seconds");
            } finally {
                router.dispose();
            }
        });
    }

    @Test
    void theResultScreenShowsTheOutcomeAndItsThreeButtonsGoWhereTheySay() throws Exception {
        List<VersusMode> rematches = new ArrayList<>();
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                VersusResultScreen result = new VersusResultScreen(router, rematches::add);
                router.addScreen(ScreenRouter.MAIN_MENU, stub());
                router.addScreen(ScreenRouter.BATTLE_SELECT, stub());
                router.addScreen(ScreenRouter.VERSUS_RESULT, result);
                router.setVisible(true);

                result.setResult(new MatchResult(Player.P2, MatchResult.Reason.TOP_OUT, 1200, 3400), VersusMode.ITEM);
                router.showScreen(ScreenRouter.VERSUS_RESULT);
                paint(result);
                result.handleKey(KeyEvent.VK_ENTER);
                assertEquals(List.of(VersusMode.ITEM), rematches, "Play again keeps the same mode");

                result.handleKey(KeyEvent.VK_RIGHT);
                result.handleKey(KeyEvent.VK_ENTER);
                assertEquals(ScreenRouter.BATTLE_SELECT, router.getCurrentScreenName(), "Second button: mode selection");

                result.setResult(new MatchResult(null, MatchResult.Reason.TIME_UP, 500, 500), VersusMode.TIME_LIMIT);
                router.showScreen(ScreenRouter.VERSUS_RESULT);
                paint(result);   // 무승부
                result.handleKey(KeyEvent.VK_RIGHT);
                result.handleKey(KeyEvent.VK_RIGHT);
                result.handleKey(KeyEvent.VK_RIGHT);
                result.handleKey(KeyEvent.VK_ENTER);
                assertEquals(ScreenRouter.MAIN_MENU, router.getCurrentScreenName(), "Third button: main menu");
            } finally {
                router.dispose();
            }
        });
    }

    @Test
    void theStartMenuVersusItemOpensModeSelection() throws Exception {
        List<String> opened = new ArrayList<>();
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                MainMenuScreen menu = new MainMenuScreen(router, () -> { }, () -> { }, () -> opened.add("versus"));
                router.addScreen(ScreenRouter.MAIN_MENU, menu);
                router.setVisible(true);
                router.showScreen(ScreenRouter.MAIN_MENU);
                // 게임 시작 → 아이템 모드 → 대전 모드
                press(menu, KeyEvent.VK_DOWN);
                press(menu, KeyEvent.VK_DOWN);
                press(menu, KeyEvent.VK_ENTER);
                assertEquals(List.of("versus"), opened);
            } finally {
                router.dispose();
            }
        });
    }

    private static void paint(JPanel panel) {
        panel.setSize(Math.max(480, panel.getWidth()), Math.max(600, panel.getHeight()));
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
    }

    private static void press(JPanel target, int keyCode) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }

    private static Screen stub() {
        JPanel panel = new JPanel();
        return new Screen() {
            @Override public JPanel getPanel() { return panel; }
            @Override public void onShow() { }
            @Override public void onHide() { }
        };
    }

    private static void edt(Runnable action) throws Exception {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }
}
