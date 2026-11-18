/*
 * 창 아이콘 테스트. PNG 세트가 모든 크기로 번들되고 ScreenRouter 창이 그 아이콘을 갖는지 검증한다.
 */
package com.team.tetris.ui;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class AppIconsTest {
    @Test
    void bundlesEverySizeAsSquarePngWithTransparency() {
        List<Image> icons = AppIcons.load();
        assertEquals(AppIcons.SIZES.length, icons.size(), "one PNG per size in icons/tetris-<size>.png");
        for (int index = 0; index < icons.size(); index++) {
            BufferedImage icon = (BufferedImage) icons.get(index);
            int size = AppIcons.SIZES[index];
            assertEquals(size, icon.getWidth(), "width of icon " + index);
            assertEquals(size, icon.getHeight(), "height of icon " + index);
            assertTrue(icon.getColorModel().hasAlpha(), "rounded corners stay transparent");
        }
        assertSame(icons, AppIcons.load(), "the set is read once and shared");
    }

    @Test
    void routerWindowCarriesTheIconsForTheTitleBarAndTaskbar() throws Exception {
        // ScreenRouter는 창이므로 headless 환경에서는 건너뛴다.
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
        SwingUtilities.invokeAndWait(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                assertEquals(AppIcons.load(), router.getIconImages(),
                        "the window gets every size so the OS can pick 16px for the title bar and 32-48px for the taskbar");
            } finally {
                router.dispose();
            }
        });
    }
}
