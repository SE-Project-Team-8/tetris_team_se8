package com.team.tetris.ui;

import java.awt.CardLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.team.tetris.common.Screen;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.settings.GameSettings;

/**
 * 등록된 화면을 하나의 창에서 관리하고 CardLayout으로 전환한다.
 * 화면 전환 시 각 Screen의 생명주기 콜백도 호출한다.
 */
public class ScreenRouter extends JFrame {

    public static final String MAIN_MENU = "MAIN_MENU";
    public static final String GAME = "GAME";
    public static final String PAUSE = "PAUSE";
    public static final String GAME_OVER = "GAME_OVER";
    public static final String NAME_INPUT = "NAME_INPUT";
    public static final String SETTINGS = "SETTINGS";
    public static final String SCOREBOARD = "SCOREBOARD";

    private final CardLayout cardLayout;
    private final JPanel containerPanel;
    private final Map<String, Screen> screens;

    private int currentWidth;
    private int currentHeight;
    private String currentScreenName;

    public ScreenRouter() {
        this(GameSettings.defaults().screenSize());
    }

    public ScreenRouter(GameSettings.ScreenSize size) {
        Objects.requireNonNull(size, "size");
        currentWidth = size.width();
        currentHeight = size.height();
        setTitle(GameConstants.GAME_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        cardLayout = new CardLayout();
        containerPanel = new JPanel(cardLayout);
        screens = new HashMap<>();

        add(containerPanel);
        setSize(currentWidth, currentHeight);
        setLocationRelativeTo(null);
    }

    /** 고유한 이름으로 화면을 등록한다. 중복 이름은 허용하지 않는다. */
    public void addScreen(String name, Screen screen) {
        validateScreenName(name);
        Objects.requireNonNull(screen, "screen");
        JPanel panel = Objects.requireNonNull(screen.getPanel(), "screen panel");

        if (screens.putIfAbsent(name, screen) != null) {
            throw new IllegalArgumentException("Screen is already registered: " + name);
        }
        containerPanel.add(panel, name);
    }

    /** 현재 화면을 숨기고 지정한 화면을 표시하며, 양쪽 화면의 콜백을 호출한다. */
    public void showScreen(String name) {
        validateScreenName(name);
        Screen nextScreen = screens.get(name);
        if (nextScreen == null) {
            throw new IllegalArgumentException("Screen is not registered: " + name);
        }
        if (name.equals(currentScreenName)) {
            return;
        }

        if (currentScreenName != null) {
            screens.get(currentScreenName).onHide();
        }
        cardLayout.show(containerPanel, name);
        currentScreenName = name;
        nextScreen.onShow();
        nextScreen.getPanel().requestFocusInWindow();
    }

    /** 창 크기와 조회용 크기 값을 함께 갱신하고 창을 화면 중앙에 다시 배치한다. */
    public void updateWindowSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Window dimensions must be positive");
        }
        this.currentWidth = width;
        this.currentHeight = height;

        setSize(width, height);
        setLocationRelativeTo(null);
        revalidate();
        repaint();
    }

    public int getCurrentWidth() {
        return currentWidth;
    }

    public int getCurrentHeight() {
        return currentHeight;
    }

    public String getCurrentScreenName() {
        return currentScreenName;
    }

    private static void validateScreenName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Screen name must not be blank");
        }
    }
}
