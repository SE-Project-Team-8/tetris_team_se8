package com.team.tetris.ui.theme;

import java.awt.Color;

/**
 * 뿌요풍 디자인의 UI 컬러 토큰. 블럭 색은 {@link com.team.tetris.block.ColorScheme}이 소유하고,
 * 여기는 배경·패널·텍스트처럼 팔레트 모드와 무관한 색만 둔다.
 *
 * 값은 docs/design/design-tokens.json의 ui 항목과 일치시킨다.
 */
public final class UiColors {
    public static final Color BACKGROUND = new Color(0x241C3B);
    public static final Color PANEL = new Color(0x332A52);
    public static final Color BOARD = new Color(0x1A1430);
    public static final Color CREAM = new Color(0xFFF6E8);
    public static final Color GARBAGE = new Color(0x8E8C96);
    public static final Color STEEL = new Color(0x4A525E);
    public static final Color STEEL_HIGHLIGHT = new Color(0x697078);
    public static final Color PLAYER1 = new Color(0x4FC3E8);
    public static final Color PLAYER2 = new Color(0xFF7F7F);
    public static final Color ACCENT = new Color(0xFFD34E);
    public static final Color TEXT_PRIMARY = new Color(0xFFF6E8);
    public static final Color TEXT_SECONDARY = new Color(0xB8AFD6);
    public static final Color TEXT_MUTED = new Color(0x9C92BE);
    public static final Color GHOST_OUTLINE = new Color(0x6E5F9E);
    public static final Color PANEL_BORDER = new Color(0x4A3D73);

    /** 블럭 위 눈·입 문자 색. */
    public static final Color CELL_GLYPH = new Color(20, 14, 38, Math.round(255 * 0.78f));
    /** 공격줄 위 `>_<` 문자 색. */
    public static final Color GARBAGE_GLYPH = new Color(20, 14, 38, Math.round(255 * 0.55f));

    /** 선택 버튼 위 글자처럼 밝은 바탕에 올라가는 어두운 글자색. */
    public static final Color TEXT_ON_ACCENT = BACKGROUND;
    /** LEVEL 수치 강조색. */
    public static final Color LEVEL = new Color(0xFFA94F);
    /** LINES 수치 강조색. */
    public static final Color LINES = new Color(0x6FD66F);

    public static final Color DIFFICULTY_EASY = new Color(0x6FD66F);
    public static final Color DIFFICULTY_NORMAL = new Color(0xFFD34E);
    public static final Color DIFFICULTY_HARD = new Color(0xD85A30);

    /** NEW 같은 강조 뱃지 배경. */
    public static final Color BADGE_NEW = new Color(0xFF7F7F);

    private UiColors() {
    }

    /**
     * 기존 색에 알파만 바꾼 새 색을 만든다.
     *
     * @param color 원본 색
     * @param alpha 0.0~1.0 불투명도
     * @return 같은 RGB에 알파를 적용한 색
     */
    public static Color withAlpha(Color color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    /**
     * 색을 지정 비율만큼 어둡게 만든다. 버튼 하단 음영과 눌림 표현에 쓴다.
     *
     * @param color 원본 색
     * @param amount 0.0(그대로)~1.0(검정)
     * @return 어두워진 색. 알파는 유지한다.
     */
    public static Color darker(Color color, float amount) {
        float keep = 1f - Math.max(0f, Math.min(1f, amount));
        return new Color(
                Math.round(color.getRed() * keep),
                Math.round(color.getGreen() * keep),
                Math.round(color.getBlue() * keep),
                color.getAlpha());
    }
}
