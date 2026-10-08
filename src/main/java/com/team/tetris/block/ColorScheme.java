package com.team.tetris.block;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 종류와 24비트 RGB를 연결하는 불변 팔레트. AWT·Swing·설정 저장소에 의존하지 않는다.
 *
 * 색상 모드는 상위 계층이 선택한다. 모드 전환으로 보드 셀의 종류·ID를 변경하지 않는다.
 * 색상값의 유일성은 시각적 식별성을 보증하지 않으므로 UI는 문자 또는 무늬를 함께 사용하고
 * 실제 화면의 적록·청황 색각 구분을 별도로 확인해야 한다.
 */
public final class ColorScheme {
    private static final int EMPTY_RGB = 0x10141C;
    private static final ColorScheme STANDARD = new ColorScheme(Map.of(
            TetrominoType.I, 0x00FFFF,
            TetrominoType.O, 0xFFFF00,
            TetrominoType.T, 0xFF00FF,
            TetrominoType.S, 0x00FF00,
            TetrominoType.Z, 0xFF0000,
            TetrominoType.J, 0x0000FF,
            TetrominoType.L, 0xFFA500), EMPTY_RGB);

    // Okabe–Ito 팔레트의 일곱 유채색. 검정은 빈 배경과 혼동할 수 있어 종류 색에서 제외한다.
    // 출처: https://jfly.uni-koeln.de/color/ (Color Universal Design, Figure 16).
    private static final ColorScheme COLOR_BLIND = new ColorScheme(Map.of(
            TetrominoType.I, 0x56B4E9,
            TetrominoType.O, 0xF0E442,
            TetrominoType.T, 0xCC79A7,
            TetrominoType.S, 0x009E73,
            TetrominoType.Z, 0xD55E00,
            TetrominoType.J, 0x0072B2,
            TetrominoType.L, 0xE69F00), EMPTY_RGB);

    private final Map<TetrominoType, Integer> colors;
    private final int emptyRgb;

    /**
     * 모든 종류의 색상을 검증하고 입력 맵과 독립적인 팔레트를 생성한다.
     *
     * @param colors 일곱 종류 모두에 대응하는 RGB. 호출 중 입력 맵의 동시 변경은 지원하지 않는다.
     * @param emptyRgb 빈 칸의 RGB
     * @throws NullPointerException 맵·키·값이 null인 경우
     * @throws InvalidColorSchemeException 종류 누락, RGB 범위 오류 또는 색상 중복인 경우
     */
    public ColorScheme(Map<TetrominoType, Integer> colors, int emptyRgb) {
        Objects.requireNonNull(colors, "colors");
        EnumMap<TetrominoType, Integer> copy = new EnumMap<>(TetrominoType.class);
        for (Map.Entry<TetrominoType, Integer> entry : colors.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "color type"),
                    Objects.requireNonNull(entry.getValue(), "color RGB"));
        }
        for (TetrominoType type : TetrominoType.values()) {
            if (!copy.containsKey(type)) {
                throw new InvalidColorSchemeException("Missing color for " + type);
            }
        }
        validateRgb(emptyRgb, "empty");
        for (Map.Entry<TetrominoType, Integer> entry : copy.entrySet()) {
            validateRgb(entry.getValue(), entry.getKey().name());
        }

        Set<Integer> used = new HashSet<>();
        used.add(emptyRgb);
        for (Map.Entry<TetrominoType, Integer> entry : copy.entrySet()) {
            // 빈 칸과 같은 색도 거부해 종류를 배경색으로 그리는 실수를 예방한다.
            if (!used.add(entry.getValue())) {
                throw new InvalidColorSchemeException(
                        "Duplicate RGB for " + entry.getKey() + ": " + entry.getValue());
            }
        }
        this.colors = Map.copyOf(copy);
        this.emptyRgb = emptyRgb;
    }

    /**
     * 일반 모드에서 사용할 종류별 색상을 제공한다.
     * @return 기본 팔레트의 공유 불변 인스턴스
     */
    public static ColorScheme standard() {
        return STANDARD;
    }

    /**
     * 색각 구분을 보조하는 Okabe–Ito 색상 매핑을 제공한다.
     * 실제 텍스트 크기·배경·문자/무늬를 포함한 UI 접근성 검증은 별도로 필요하다.
     *
     * @return 색맹 모드 팔레트의 공유 불변 인스턴스
     * @see "Okabe·Ito의 Color Universal Design: https://jfly.uni-koeln.de/color/"
     */
    public static ColorScheme colorBlind() {
        return COLOR_BLIND;
    }

    /**
     * 블록 종류에 대응하는 RGB를 조회한다. 생성 시 완전성을 검증하므로 재검사는 필요 없다.
     *
     * @param type 조회할 종류
     * @return 해당 종류의 0x000000~0xFFFFFF RGB
     * @throws NullPointerException 종류가 null인 경우
     */
    public int rgbOf(TetrominoType type) {
        return colors.get(Objects.requireNonNull(type, "type"));
    }

    /**
     * 종류가 없는 셀을 그릴 때 사용할 배경색을 조회한다.
     * @return 빈 칸의 24비트 RGB
     */
    public int emptyRgb() {
        return emptyRgb;
    }

    private static void validateRgb(int rgb, String subject) {
        if (rgb < 0 || rgb > 0xFFFFFF) {
            throw new InvalidColorSchemeException(
                    "RGB must be in 0x000000..0xFFFFFF for " + subject + ": " + rgb);
        }
    }
}
