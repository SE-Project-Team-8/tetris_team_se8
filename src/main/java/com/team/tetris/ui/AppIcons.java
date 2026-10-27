package com.team.tetris.ui;

import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * 실행 중인 창과 Dock에 쓰는 앱 아이콘.
 * jpackage의 {@code --icon}은 실행파일(exe·app 번들)의 아이콘만 바꾸므로, 창 제목줄과 작업표시줄에 보이는
 * 아이콘은 창에 직접 설정해야 한다. ImageIO는 .ico·.icns를 읽지 못하므로 같은 그림의 PNG 세트를 쓴다.
 */
public final class AppIcons {
    /** Windows는 제목줄에 16px, 작업표시줄에 DPI에 따라 32~48px를 고르므로 여러 크기를 함께 넘긴다. */
    static final int[] SIZES = {16, 32, 48, 64, 128, 256};
    private static final String RESOURCE_FORMAT = "/icons/tetris-%d.png";

    private AppIcons() { }

    /** 한 번만 읽어 두고 모든 창이 같은 이미지를 쓴다. */
    private static final class Holder {
        static final List<Image> IMAGES = read();
    }

    /** 읽을 수 있는 크기만 작은 것부터 돌려준다. 리소스가 빠져도 창은 기본 아이콘으로 떠야 하므로 예외를 던지지 않는다. */
    public static List<Image> load() {
        return Holder.IMAGES;
    }

    /** 제목줄·작업표시줄 아이콘(Windows·Linux). macOS는 창 아이콘을 그리지 않지만 설정해도 해가 없다. */
    public static void apply(Window window) {
        List<Image> images = load();
        if (!images.isEmpty()) {
            window.setIconImages(images);
        }
    }

    /**
     * Dock 아이콘(macOS). .app 번들은 icns를 쓰지만 jar로 직접 실행하면 자바 기본 아이콘이 뜬다.
     * Windows는 이 기능을 지원하지 않으므로 {@link #apply(Window)}가 작업표시줄을 맡는다.
     */
    public static void applyToTaskbar() {
        if (!Taskbar.isTaskbarSupported()) {
            return;
        }
        Taskbar taskbar = Taskbar.getTaskbar();
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            return;
        }
        List<Image> images = load();
        if (!images.isEmpty()) {
            taskbar.setIconImage(images.get(images.size() - 1));
        }
    }

    private static List<Image> read() {
        List<Image> images = new ArrayList<>();
        for (int size : SIZES) {
            String name = String.format(Locale.ROOT, RESOURCE_FORMAT, size);
            try (InputStream in = AppIcons.class.getResourceAsStream(name)) {
                if (in == null) {
                    continue;
                }
                Image image = ImageIO.read(in);
                if (image != null) {
                    images.add(image);
                }
            } catch (IOException ignored) {
                // 손상된 파일 하나 때문에 나머지 크기까지 버리지 않는다.
            }
        }
        return List.copyOf(images);
    }
}
