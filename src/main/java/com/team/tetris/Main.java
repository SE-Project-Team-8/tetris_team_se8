/*
 * 애플리케이션 조립부. 엔진·화면·설정·순위 저장소·아이템 목록을 만들고 연결한다.
 */
package com.team.tetris;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.team.tetris.block.ColorScheme;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.GameMode;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellPosition;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.game.versus.VersusInput;
import com.team.tetris.game.versus.MatchResult;
import com.team.tetris.game.versus.VersusMatch;
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
import com.team.tetris.ui.BattleSelectScreen;
import com.team.tetris.ui.VersusCheckScreen;
import com.team.tetris.ui.VersusResultScreen;
import com.team.tetris.ui.VersusScreen;
import com.team.tetris.ui.theme.ThemedDialog;

/** Composition root for the game engine, screens, settings, and ranking storage. */
public final class Main {
    private Main() { }

    /** 확인용 대전 화면을 띄우는 실행 인자의 앞부분. 예: {@code --versus=normal} */
    static final String VERSUS_ARGUMENT = "--versus=";

    public static void main(String[] args) {
        Optional<VersusMode> versus;
        try {
            versus = versusModeFrom(args);
        } catch (IllegalArgumentException invalid) {
            System.err.println(invalid.getMessage());
            versus = Optional.empty();
        }
        Optional<VersusMode> versusMode = versus;
        SwingUtilities.invokeLater(() -> new Application(versusMode).show());
    }

    /**
     * 실행 인자에서 확인용 대전 화면의 방식을 찾는다 (docs/battle/ui-contract.md 5절). 정식 진입 경로는 UI 담당이 만드는
     * 시작 메뉴 항목이고, 그 전까지 대전은 이 인자로만 띄운다.
     *
     * @return {@code --versus=normal|item|time}이 있으면 그 방식
     * @throws IllegalArgumentException 방식 이름을 알 수 없을 때
     */
    static Optional<VersusMode> versusModeFrom(String... args) {
        for (String arg : args) {
            if (!arg.startsWith(VERSUS_ARGUMENT)) continue;
            String name = arg.substring(VERSUS_ARGUMENT.length()).toLowerCase(Locale.ROOT);
            return Optional.of(switch (name) {
                case "normal" -> VersusMode.NORMAL;
                case "item" -> VersusMode.ITEM;
                case "time" -> VersusMode.TIME_LIMIT;
                default -> throw new IllegalArgumentException(
                        "Unknown versus mode '" + name + "'. Use " + VERSUS_ARGUMENT + "normal, item or time.");
            });
        }
        return Optional.empty();
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
        // 로컬 대전: 두 플레이어가 같은 아이템 목록과 설정의 난이도를 쓴다 (D-11).
        private final VersusMatch versusMatch = new VersusMatch(VersusMatch.standard(items, () -> settings.difficulty()));
        private final VersusInput versusInput = new VersusInput(versusMatch, settings.versusKeyMap());
        private final Optional<VersusMode> versusCheck;
        // 로컬 대전 화면: 방식 선택 → 대전 → 승자 화면.
        private final VersusResultScreen versusResultScreen = new VersusResultScreen(router, this::startVersus);
        private final VersusScreen versusScreen = new VersusScreen(router, versusMatch, versusInput, () -> settings,
                items, this::showVersusResult);
        private final BattleSelectScreen battleSelectScreen =
                new BattleSelectScreen(router, () -> settings, this::startVersus);
        private int finalScore;
        private int bestScoreLoadGeneration;
        private int bestScore = -1;
        private Difficulty finalDifficulty = Difficulty.NORMAL;
        private GameMode currentMode = GameMode.NORMAL;
        private GameMode finalMode = GameMode.NORMAL;

        private Application(Optional<VersusMode> versusCheck) {
            this.versusCheck = Objects.requireNonNull(versusCheck, "versusCheck");
            listener.setDelegate(gameScreen.eventListener());
            applySettings(settings);
            router.addScreen(ScreenRouter.MAIN_MENU, new MainMenuScreen(router,
                    () -> startGame(false), () -> startGame(true),
                    () -> router.showScreen(ScreenRouter.BATTLE_SELECT)));
            router.addScreen(ScreenRouter.GAME, gameScreen);
            router.addScreen(ScreenRouter.PAUSE,
                    new PauseScreen(router, engine, () -> settings.keyBindings()));
            router.addScreen(ScreenRouter.GAME_OVER, gameOverScreen);
            router.addScreen(ScreenRouter.NAME_INPUT, nameInputScreen);
            router.addScreen(ScreenRouter.SETTINGS,
                    new SettingsScreen(router, settingsRepository, scoreboardRepository,
                            () -> settings, this::applySettings, this::onScoresReset));
            router.addScreen(ScreenRouter.SCOREBOARD, scoreboardScreen);
            router.addScreen(ScreenRouter.BATTLE_SELECT, battleSelectScreen);
            router.addScreen(ScreenRouter.VERSUS, versusScreen);
            router.addScreen(ScreenRouter.VERSUS_RESULT, versusResultScreen);
            versusCheck.ifPresent(mode -> router.addScreen(VersusCheckScreen.NAME,
                    new VersusCheckScreen(router, versusMatch, versusInput, () -> settings, mode)));
            router.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent event) {
                    engine.close();
                    versusMatch.close();
                }
            });
        }

        /** 승자 화면의 "다시 대전": 같은 방식, 같은 제한 시간으로 새 대전을 연다. */
        private void startVersus(VersusMode mode) {
            versusScreen.prepare(mode);
            router.showScreen(ScreenRouter.VERSUS);
        }

        /** 방식 선택 화면: 고른 방식과 시간제한 방식의 제한 시간(초)으로 새 대전을 연다. */
        private void startVersus(VersusMode mode, int timeLimitSeconds) {
            versusScreen.prepare(mode, timeLimitSeconds);
            router.showScreen(ScreenRouter.VERSUS);
        }

        private void showVersusResult(MatchResult result) {
            versusResultScreen.setResult(result, versusMatch.mode());
            router.showScreen(ScreenRouter.VERSUS_RESULT);
        }

        private void show() {
            router.setVisible(true);
            router.showScreen(versusCheck.isPresent() ? VersusCheckScreen.NAME : ScreenRouter.MAIN_MENU);
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
                            ThemedDialog.showMessage(router, "스코어보드 오류",
                                    "최고 점수를 불러오지 못했습니다.", ThemedDialog.Tone.ERROR);
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
            gameScreen.setGameMode(currentMode);
            // No notice until this ranking's best score is loaded; the previous game's may differ.
            gameScreen.setBestScore(-1);
            refreshBestScore(false);
            gameScreen.startNewGame();
        }

        private GameSettings loadSettings() {
            try {
                return settingsRepository.load();
            } catch (IOException failure) {
                boolean reset = ThemedDialog.showConfirm(null, "설정 오류",
                        "설정을 불러오지 못했습니다.\n기본값으로 되돌릴까요?", ThemedDialog.Tone.ERROR,
                        "기본값 복원", "그대로 시작");
                if (reset) {
                    try {
                        return settingsRepository.reset();
                    } catch (IOException saveFailure) {
                        ThemedDialog.showMessage(null, "설정 오류", "기본 설정을 저장하지 못했습니다.",
                                ThemedDialog.Tone.ERROR);
                    }
                }
                return GameSettings.defaults();
            }
        }

        private void applySettings(GameSettings updated) {
            settings = Objects.requireNonNull(updated, "updated");
            router.updateWindowSize(updated.screenSize().width(), updated.screenSize().height());
            gameScreen.updateKeyBindings(updated.keyBindings());
            versusInput.setKeys(updated.versusKeyMap());
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
                        int choice = ThemedDialog.showOptions(router, "스코어보드 오류",
                                "저장된 점수를 읽지 못했습니다.", ThemedDialog.Tone.ERROR, 0,
                                "다시 시도", "메인 메뉴");
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
                        nameInputScreen.showError("점수를 저장하지 못했습니다. 다시 시도해 주세요.");
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
        @Override public void onFeverActivated(int seconds) { delegate.onFeverActivated(seconds); }
        @Override public void onSlowDownActivated(int previousLevel, int currentLevel) {
            delegate.onSlowDownActivated(previousLevel, currentLevel);
        }
        @Override public void onHardDropped(BoardSnapshot fallingBefore, BoardSnapshot fallingAfter) {
            delegate.onHardDropped(fallingBefore, fallingAfter);
        }
        @Override public void onBombExploded(CellPosition center, List<CellPosition> blastCells) {
            delegate.onBombExploded(center, blastCells);
        }
        @Override public void onAnvilCrushed(List<CellPosition> crushedCells) {
            delegate.onAnvilCrushed(crushedCells);
        }
        @Override public void onGameOver(int score) { delegate.onGameOver(score); }
    }
}
