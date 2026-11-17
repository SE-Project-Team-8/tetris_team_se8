package com.team.tetris.ui.theme;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 디자인 스펙 §5의 세 가지 서체를 제공한다.
 * <ul>
 *   <li>display — Jua. 제목·버튼·뱃지</li>
 *   <li>body — Noto Sans KR 계열 시스템 폰트. 본문·설명</li>
 *   <li>mono — IBM Plex Mono Bold. 보드 셀 문자·점수 숫자</li>
 * </ul>
 * 번들 폰트는 처음 쓸 때 한 번만 읽는다. 읽기에 실패하면 SANS_SERIF/MONOSPACED로 대체한다.
 */
public final class UiFonts {
    private static final String JUA_PATH = "/fonts/Jua-Regular.ttf";
    private static final String PLEX_MONO_PATH = "/fonts/IBMPlexMono-Bold.ttf";
    // 설치 여부가 환경마다 달라 번들하지 않는다. 앞에 있는 이름을 우선한다.
    private static final String[] BODY_CANDIDATES = {"Noto Sans KR", "Malgun Gothic", "맑은 고딕", "Apple SD Gothic Neo"};
    // 셀마다 deriveFont를 부르면 매 프레임 수백 개의 Font가 생기므로 크기별로 재사용한다.
    private static final Map<Float, Font> DISPLAY_CACHE = new ConcurrentHashMap<>();
    private static final Map<Float, Font> BODY_CACHE = new ConcurrentHashMap<>();
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
     * 본문·설명용 서체를 반환한다.
     *
     * @param size 포인트 크기
     * @return 설치된 한글 본문 서체 또는 SANS_SERIF
     */
    public static Font body(float size) {
        return BODY_CACHE.computeIfAbsent(size, Holder.BODY::deriveFont);
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
        return body(size).deriveFont(Font.BOLD);
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
        return body(size).deriveFont(Font.BOLD);
    }

    // 클래스 로딩 시점까지 폰트 파일 읽기를 미룬다.
    private static final class Holder {
        static final Font DISPLAY = load(JUA_PATH, new Font(Font.SANS_SERIF, Font.BOLD, 12));
        static final Font MONO = load(PLEX_MONO_PATH, new Font(Font.MONOSPACED, Font.BOLD, 12));
        static final Font BODY = pickBody();

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

        private static Font pickBody() {
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
