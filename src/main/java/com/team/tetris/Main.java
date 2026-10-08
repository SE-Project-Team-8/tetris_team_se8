package com.team.tetris;

import com.team.tetris.block.ColorScheme;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameEngine;
import com.team.tetris.scoreboard.ScoreRecord;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.SettingsRepository;
import com.team.tetris.ui.GameOverScreen;
import com.team.tetris.ui.GameScreen;
import com.team.tetris.ui.MainMenuScreen;
import com.team.tetris.ui.NameInputScreen;
import com.team.tetris.ui.PauseScreen;
import com.team.tetris.ui.ScoreboardScreen;
import com.team.tetris.ui.ScreenRouter;
import com.team.tetris.ui.SettingsScreen;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/** Composition root for the game engine, screens, settings, and ranking storage. */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Application().show());
    }

    private static final class Application {
        private final SettingsRepository settingsRepository = new SettingsRepository();
        private final ScoreboardRepository scoreboardRepository = new ScoreboardRepository();
        private GameSettings settings = loadSettings();
        private final ScreenRouter router = new ScreenRouter(settings.screenSize());
        private final ForwardingListener listener = new ForwardingListener();
        private final GameEngine engine = new GameEngine(new BlockBoardDriver(), listener);
        private final GameScreen gameScreen = new GameScreen(router, engine, settings.keyBindings(), this::onGameOver);
        private final GameOverScreen gameOverScreen = new GameOverScreen(router);
        private final NameInputScreen nameInputScreen = new NameInputScreen(router, this::saveScore);
        private final ScoreboardScreen scoreboardScreen = new ScoreboardScreen(router, scoreboardRepository);
        private int finalScore;

        private Application() {
            listener.setDelegate(gameScreen.eventListener());
            applySettings(settings);
            router.addScreen(ScreenRouter.MAIN_MENU, new MainMenuScreen(router));
            router.addScreen(ScreenRouter.GAME, gameScreen);
            router.addScreen(ScreenRouter.PAUSE,
                    new PauseScreen(router, engine, () -> settings.keyBindings()));
            router.addScreen(ScreenRouter.GAME_OVER, gameOverScreen);
            router.addScreen(ScreenRouter.NAME_INPUT, nameInputScreen);
            router.addScreen(ScreenRouter.SETTINGS,
                    new SettingsScreen(router, settingsRepository, scoreboardRepository,
                            () -> settings, this::applySettings));
            router.addScreen(ScreenRouter.SCOREBOARD, scoreboardScreen);
            router.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent event) {
                    engine.close();
                }
            });
        }

        private void show() {
            router.setVisible(true);
            router.showScreen(ScreenRouter.MAIN_MENU);
        }

        private GameSettings loadSettings() {
            try {
                return settingsRepository.load();
            } catch (IOException failure) {
                int reset = JOptionPane.showConfirmDialog(null,
                        "Could not load settings. Restore defaults?", "Settings error",
                        JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                if (reset == JOptionPane.YES_OPTION) {
                    try {
                        return settingsRepository.reset();
                    } catch (IOException saveFailure) {
                        JOptionPane.showMessageDialog(null, "Could not save default settings.");
                    }
                }
                return GameSettings.defaults();
            }
        }

        private void applySettings(GameSettings updated) {
            settings = Objects.requireNonNull(updated, "updated");
            router.updateWindowSize(updated.screenSize().width(), updated.screenSize().height());
            gameScreen.updateKeyBindings(updated.keyBindings());
            gameScreen.setColorScheme(updated.isColorBlindModeEnabled()
                    ? ColorScheme.colorBlind() : ColorScheme.standard());
        }

        private void onGameOver(int score) {
            finalScore = score;
            int level = engine.getLevel();
            int lines = engine.getClearedLines();
            // Avoid changing screens from inside the engine's notification stack.
            SwingUtilities.invokeLater(() -> checkRanking(score, level, lines));
        }

        private void checkRanking(int score, int level, int lines) {
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() throws Exception {
                    return scoreboardRepository.qualifies(score);
                }

                @Override protected void done() {
                    try {
                        boolean qualifies = get();
                        gameOverScreen.setGameResult(score, level, lines, qualifies);
                        router.showScreen(ScreenRouter.GAME_OVER);
                    } catch (Exception failure) {
                        int choice = JOptionPane.showOptionDialog(router,
                                "Could not read saved scores.", "Scoreboard error",
                                JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null,
                                new String[]{"Retry", "Main Menu"}, "Retry");
                        if (choice == 0) checkRanking(score, level, lines);
                        else router.showScreen(ScreenRouter.MAIN_MENU);
                    }
                }
            }.execute();
        }

        private void saveScore(String name) {
            new SwingWorker<Optional<ScoreRecord>, Void>() {
                @Override protected Optional<ScoreRecord> doInBackground() throws Exception {
                    return scoreboardRepository.add(name, finalScore);
                }

                @Override protected void done() {
                    try {
                        scoreboardScreen.highlight(get().map(ScoreRecord::id).orElse(null));
                        router.showScreen(ScreenRouter.SCOREBOARD);
                    } catch (Exception failure) {
                        nameInputScreen.showError("Could not save score. Check the name and retry.");
                    }
                }
            }.execute();
        }
    }

    private static final class ForwardingListener implements GameEventListener {
        private GameEventListener delegate;

        void setDelegate(GameEventListener delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override public void onBoardUpdated(BoardSnapshot snapshot) { delegate.onBoardUpdated(snapshot); }
        @Override public void onScoreChanged(int score) { delegate.onScoreChanged(score); }
        @Override public void onNextBlocksChanged(int[] types) { delegate.onNextBlocksChanged(types); }
        @Override public void onPauseStateChanged(boolean paused) { delegate.onPauseStateChanged(paused); }
        @Override public void onGameOver(int score) { delegate.onGameOver(score); }
    }
}
