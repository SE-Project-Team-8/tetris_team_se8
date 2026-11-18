package com.team.tetris.ui.theme;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.io.IOException;
import java.io.InputStream;
import java.text.AttributedString;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 디자인 스펙 §5의 세 가지 서체를 제공한다.
 * <ul>
 *   <li>display — Jua. 제목·버튼·뱃지</li>
 *   <li>body — 본문·설명. 화면 전체 글자 느낌을 둥글게 맞추려고 display와 같은 Jua를 쓴다</li>
 *   <li>mono — IBM Plex Mono Bold. 보드 셀 문자·점수 숫자</li>
 * </ul>
 * Jua에는 ·, ←→↑↓, ▶, × 같은 기호와 자주 쓰지 않는 한글 음절이 없다. 그런 글자는
 * {@link #withFallback}로 그 글자만 시스템 한글 서체(Noto Sans KR 계열)로 바꿔 그린다.
 * 번들 폰트는 처음 쓸 때 한 번만 읽는다. 읽기에 실패하면 SANS_SERIF/MONOSPACED로 대체한다.
 */
public final class UiFonts {
    private static final String JUA_PATH = "/fonts/Jua-Regular.ttf";
    private static final String PLEX_MONO_PATH = "/fonts/IBMPlexMono-Bold.ttf";
    // 설치 여부가 환경마다 달라 번들하지 않는다. 앞에 있는 이름을 우선한다.
    private static final String[] BODY_CANDIDATES = {"Noto Sans KR", "Malgun Gothic", "맑은 고딕", "Apple SD Gothic Neo"};
    // 셀마다 deriveFont를 부르면 매 프레임 수백 개의 Font가 생기므로 크기별로 재사용한다.
    private static final Map<Float, Font> DISPLAY_CACHE = new ConcurrentHashMap<>();
    private static final Map<Float, Font> SYSTEM_CACHE = new ConcurrentHashMap<>();
    private static final Map<Float, Font> MONO_CACHE = new ConcurrentHashMap<>();

    private UiFonts() {
    }

    /**
     * 제목·버튼용 둥근 서체를 지정 크기로 반환한다.
     *
     * @param size 포인트 크기
     * @return Jua 또는 대체 서체
     */
    public static Font display(float size) {
        return DISPLAY_CACHE.computeIfAbsent(size, Holder.DISPLAY::deriveFont);
    }

    /**
     * 본문·설명용 서체를 반환한다. Jua에 없는 기호가 섞일 수 있으면 {@link #withFallback}로 그린다.
     *
     * @param size 포인트 크기
     * @return Jua 또는 대체 서체
     */
    public static Font body(float size) {
        return display(size);
    }

    /**
     * Jua에 없는 글자를 대신 그릴 시스템 한글 서체.
     *
     * @param size 포인트 크기
     * @return 설치된 한글 본문 서체 또는 SANS_SERIF
     */
    public static Font system(float size) {
        return SYSTEM_CACHE.computeIfAbsent(size, Holder.SYSTEM::deriveFont);
    }

    /**
     * 셀 문자와 수치용 고정폭 굵은 서체를 반환한다.
     *
     * @param size 포인트 크기
     * @return IBM Plex Mono Bold 또는 대체 서체
     */
    public static Font mono(float size) {
        return MONO_CACHE.computeIfAbsent(size, Holder.MONO::deriveFont);
    }

    /**
     * 사용자 입력 문자열(플레이어 이름 등)을 그릴 서체. IBM Plex Mono에는 한글이 없고 번들 폰트는
     * 다른 서체로 대체 렌더링되지 않으므로, 그릴 수 없는 글자가 있으면 굵은 본문 서체를 쓴다.
     *
     * @param text 그릴 문자열
     * @param size 포인트 크기
     * @return 문자열 전체를 그릴 수 있는 서체
     */
    public static Font monoOrFallback(String text, float size) {
        Font mono = mono(size);
        if (text == null || mono.canDisplayUpTo(text) == -1) {
            return mono;
        }
        return system(size).deriveFont(Font.BOLD);
    }

    /**
     * 뱃지처럼 기호(×, ▼ 등)가 섞일 수 있는 제목용 서체. Jua에 없는 글자가 있으면 굵은 본문 서체를 쓴다.
     *
     * @param text 그릴 문자열
     * @param size 포인트 크기
     * @return 문자열 전체를 그릴 수 있는 서체
     */
    public static Font displayOrFallback(String text, float size) {
        Font display = display(size);
        if (text == null || display.canDisplayUpTo(text) == -1) {
            return display;
        }
        return system(size).deriveFont(Font.BOLD);
    }

    /**
     * {@code font}로 그리되 그 서체에 없는 글자만 같은 크기·스타일의 시스템 서체로 바꾼 서식 문자열.
     * Graphics2D.drawString(AttributedCharacterIterator, ...)나 TextLayout으로 그린다.
     *
     * @param text 그릴 문자열(비어 있지 않아야 한다)
     * @param font 기본 서체
     * @return 글자 구간마다 서체가 지정된 문자열
     */
    public static AttributedString withFallback(String text, Font font) {
        AttributedString styled = new AttributedString(text);
        styled.addAttribute(TextAttribute.FONT, font);
        Font fallback = null;
        int index = 0;
        while (index < text.length()) {
            int missing = font.canDisplayUpTo(text.substring(index));
            if (missing == -1) break;
            int start = index + missing;
            int end = start;
            while (end < text.length() && !font.canDisplay(text.codePointAt(end))) {
                end += Character.charCount(text.codePointAt(end));
            }
            if (fallback == null) fallback = fallbackFor(font);
            styled.addAttribute(TextAttribute.FONT, fallback, start, end);
            index = end;
        }
        return styled;
    }

    /**
     * {@link #withFallback}로 그렸을 때의 문자열 폭.
     *
     * @return 폭(px). 빈 문자열이면 0
     */
    public static int width(String text, Font font, FontRenderContext context) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(new TextLayout(withFallback(text, font).getIterator(), context).getAdvance());
    }

    // 자간 같은 속성은 그대로 두고 글꼴 이름만 시스템 서체로 바꾼다.
    private static Font fallbackFor(Font font) {
        Map<TextAttribute, Object> attributes = new HashMap<>(font.getAttributes());
        attributes.put(TextAttribute.FAMILY, Holder.SYSTEM.getFamily());
        return Font.getFont(attributes);
    }

    // 클래스 로딩 시점까지 폰트 파일 읽기를 미룬다.
    private static final class Holder {
        static final Font DISPLAY = load(JUA_PATH, new Font(Font.SANS_SERIF, Font.BOLD, 12));
        static final Font MONO = load(PLEX_MONO_PATH, new Font(Font.MONOSPACED, Font.BOLD, 12));
        static final Font SYSTEM = pickSystem();

        private static Font load(String path, Font fallback) {
            try (InputStream in = UiFonts.class.getResourceAsStream(path)) {
                if (in == null) {
                    return fallback;
                }
                Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font;
            } catch (IOException | FontFormatException e) {
                return fallback;
            }
        }

        private static Font pickSystem() {
            Set<String> installed;
            try {
                installed = Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames())
                        .collect(Collectors.toSet());
            } catch (RuntimeException e) {
                installed = Set.of();
            }
            for (String name : BODY_CANDIDATES) {
                if (installed.contains(name)) {
                    return new Font(name, Font.PLAIN, 12);
                }
            }
            return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
    }
}
