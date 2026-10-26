/*
 * 애플리케이션 조립부. 엔진·화면·설정·순위 저장소·아이템 목록을 만들고 연결한다.
 */
package com.team.tetris;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.team.tetris.block.ColorScheme;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.GameMode;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.scoreboard.ScoreRecord;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.SettingsRepository;
import com.team.tetris.ui.AppIcons;
import com.team.tetris.ui.GameOverScreen;
import com.team.tetris.ui.GameScreen;
import com.team.tetris.ui.MainMenuScreen;
import com.team.tetris.ui.NameInputScreen;
import com.team.tetris.ui.PauseScreen;
import com.team.tetris.ui.ScoreboardScreen;
import com.team.tetris.ui.ScreenRouter;
import com.team.tetris.ui.SettingsScreen;

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
        private final ItemRegistry items = ItemRegistry.defaults();
        private final BlockBoardDriver boardDriver = new BlockBoardDriver(items);
        private final GameEngine engine = new GameEngine(boardDriver, listener, () -> settings.difficulty());
        private final GameScreen gameScreen = new GameScreen(router, engine, settings.keyBindings(), this::onGameOver, items);
        private final GameOverScreen gameOverScreen = new GameOverScreen(router);
        private final NameInputScreen nameInputScreen = new NameInputScreen(router, this::saveScore);
        private final ScoreboardScreen scoreboardScreen = new ScoreboardScreen(router, scoreboardRepository);
        private int finalScore;
        private int bestScoreLoadGeneration;
        private int bestScore = -1;
        private Difficulty finalDifficulty = Difficulty.NORMAL;
        private GameMode currentMode = GameMode.NORMAL;
        private GameMode finalMode = GameMode.NORMAL;

        private Application() {
            listener.setDelegate(gameScreen.eventListener());
            applySettings(settings);
            router.addScreen(ScreenRouter.MAIN_MENU, new MainMenuScreen(router,
                    () -> startGame(false), () -> startGame(true)));
            router.addScreen(ScreenRouter.GAME, gameScreen);
            router.addScreen(ScreenRouter.PAUSE,
                    new PauseScreen(router, engine, () -> settings.keyBindings()));
            router.addScreen(ScreenRouter.GAME_OVER, gameOverScreen);
            router.addScreen(ScreenRouter.NAME_INPUT, nameInputScreen);
            router.addScreen(ScreenRouter.SETTINGS,
                    new SettingsScreen(router, settingsRepository, scoreboardRepository,
                            () -> settings, this::applySettings, this::onScoresReset));
            router.addScreen(ScreenRouter.SCOREBOARD, scoreboardScreen);
            router.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent event) {
                    engine.close();
                }
            });
        }

        private void show() {
            AppIcons.applyToTaskbar();
            router.setVisible(true);
            router.showScreen(ScreenRouter.MAIN_MENU);
            refreshBestScore(true);
        }

        /** The best-score notice compares against the ranking of the mode and difficulty being played. */
        private void refreshBestScore(boolean showError) {
            int generation = ++bestScoreLoadGeneration;
            Difficulty difficulty = settings.difficulty();
            GameMode mode = currentMode;
            new SwingWorker<Integer, Void>() {
                @Override protected Integer doInBackground() throws Exception {
                    List<ScoreRecord> records = scoreboardRepository.load(difficulty, mode);
                    return records.isEmpty() ? -1 : records.getFirst().score();
                }

                @Override protected void done() {
                    if (generation != bestScoreLoadGeneration) {
                        return;
                    }
                    try {
                        bestScore = get();
                        gameScreen.setBestScore(bestScore);
                    } catch (Exception failure) {
                        if (showError) {
                            JOptionPane.showMessageDialog(router, "Could not load the best score.",
                                    "Scoreboard error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }.execute();
        }

        private void onScoresReset() {
            bestScoreLoadGeneration++;
            bestScore = -1;
            gameScreen.setBestScore(bestScore);
        }

        private void startGame(boolean itemMode) {
            currentMode = itemMode ? GameMode.ITEM : GameMode.NORMAL;
            boardDriver.setItemMode(itemMode);
            // No notice until this ranking's best score is loaded; the previous game's may differ.
            gameScreen.setBestScore(-1);
            refreshBestScore(false);
            gameScreen.startNewGame();
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
            finalDifficulty = engine.getDifficulty();
            finalMode = currentMode;
            int level = engine.getLevel();
            int lines = engine.getClearedLines();
            // Avoid changing screens from inside the engine's notification stack.
            SwingUtilities.invokeLater(() -> checkRanking(score, level, lines));
        }

        private void checkRanking(int score, int level, int lines) {
            Difficulty difficulty = finalDifficulty;
            GameMode mode = finalMode;
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() throws Exception {
                    return scoreboardRepository.qualifies(score, difficulty, mode);
                }

                @Override protected void done() {
                    try {
                        boolean qualifies = get();
                        // Both "Enter name" and "View scoreboard" lead to the ranking just played.
                        scoreboardScreen.select(mode, difficulty);
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
            int scoreToSave = finalScore;
            Difficulty difficultyToSave = finalDifficulty;
            GameMode modeToSave = finalMode;
            new SwingWorker<Optional<ScoreRecord>, Void>() {
                @Override protected Optional<ScoreRecord> doInBackground() throws Exception {
                    return scoreboardRepository.add(name, scoreToSave, difficultyToSave, modeToSave);
                }

                @Override protected void done() {
                    try {
                        Optional<ScoreRecord> savedRecord = get();
                        scoreboardScreen.highlight(savedRecord.map(ScoreRecord::id).orElse(null));
                        if (savedRecord.isPresent()) {
                            refreshBestScore(true);
                        }
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
        @Override public void onLandingUpdated(BoardSnapshot falling, BoardSnapshot landing) {
            delegate.onLandingUpdated(falling, landing);
        }
        @Override public void onLinesCleared(List<Integer> rows) { delegate.onLinesCleared(rows); }
        @Override public void onLinesCleared(List<Integer> rows, BoardSnapshot snapshot) {
            delegate.onLinesCleared(rows, snapshot);
        }
        @Override public void onScoreChanged(int score) { delegate.onScoreChanged(score); }
        @Override public void onNextBlocksChanged(int[] types) { delegate.onNextBlocksChanged(types); }
        @Override public void onPauseStateChanged(boolean paused) { delegate.onPauseStateChanged(paused); }
        @Override public void onGameOver(int score) { delegate.onGameOver(score); }
    }
}
