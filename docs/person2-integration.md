# 담당자 2: 게임 진행 / 점수 / 영속화

Java 21, 기존 패키지와 `GameEventListener` 계약을 유지한다. 이번 구현 범위는 요청한
`GameEngine`, `GameState`, `ScoreCalculator`, `GameSettings`, `SettingsRepository`,
`KeyBindings`, `ScoreRecord`, `ScoreboardRepository`이다. 이후 정책 교체를 위해 `GameAction`,
`ScoringPolicy`, `SpeedPolicy`, `DefaultSpeedPolicy`를 추가했다. 공통 파일 저장 코드는
`common/persistence/PropertiesFile`에 모았다.

현재 체크아웃의 `block`과 실제 게임 UI는 아직 뼈대이므로 이 변경만으로 플레이 가능한 게임이 실행되지는 않는다.
`Main`의 placeholder, `SettingsScreen`, `ScoreboardScreen`을 포함한 화면 구현은 이번 변경에
포함하지 않는다. 아래 API와 연결 지점을 이용하여 화면과 블록 구현을 통합한다.

## 디렉터리와 의존성

| 위치 (`src/main/java/com/team/tetris/` 기준) | 책임 |
| --- | --- |
| `block/` | 담당자 1: 보드, 이동/회전/충돌, 생성, 줄 삭제, 색상 |
| `game/` | 담당자 2: 타이머, 상태, 조작 전달, 점수/속도 정책 |
| `settings/` | 담당자 2: 불변 설정/키 매핑, 파일 저장과 기본값 복원 |
| `scoreboard/` | 담당자 2: 기록 유효성, 상위 순위, 저장/조회/초기화 |
| `common/events/` | 기존 게임 → UI 단방향 알림 계약 |
| `common/persistence/` | UTF-8 properties 읽기 및 임시 파일 교체 저장 |
| `ui/`, `Main.java` | 화면/입력 연결, 이름 입력, 화면 이동 |
| `src/test/java/com/team/tetris/` | 각 패키지 JUnit 5 테스트 |

`game`은 `ui`를 import하지 않는다. `block`은 `game`, `settings`, `scoreboard`, `ui`를
import하지 않는다. 어댑터를 `game` 패키지에 두고 `block` 객체를 조합하면 이 규칙을 지킬 수 있다.
기존 `common` 이벤트의 시그니처는 변경하지 않았다.

게임 입력은 `GameAction`으로 표현한다. `settings.KeyBindings`는 물리 키를 `GameAction`으로
변환하지만 엔진은 설정 패키지를 import하지 않는다. 화면 버튼도 동일한 `GameAction`을 엔진에
전달할 수 있다. `GameEngine.handleKey`는 제거했고 UI가 변환을 담당한다. 새 `GameAction`을
추가하면 엔진의 exhaustive switch에서 처리가 빠진 경우 컴파일 단계에 표시된다.

점수 계산은 `ScoringPolicy`와 기본 구현 `ScoreCalculator`, 속도 계산은 `SpeedPolicy`와 기본
구현 `DefaultSpeedPolicy`로 나뉜다. 엔진은 규칙 계산 결과를 사용해 타이머와 점수 표시를 갱신한다.
다른 모드를 구현할 때 새 정책 구현을 생성자에 전달하면 된다.

```java
GameEngine engine = new GameEngine(boardDriver, listener,
        new ScoreCalculator(), new DefaultSpeedPolicy(), timer);
```

`ScoringPolicy.add`는 최종 점수만 더하며, 생성 블록 수와 삭제 줄 수는 엔진이 별도로 센다.
점수를 주지 않는 0칸 낙하에서는 `add`를 호출하지 않는다. 두 정책은 순수 계산으로 작성해야
타이머 호출과 UI 갱신 중에도 예측 가능한 결과를 얻을 수 있다.

## 요구사항과 선택한 규칙

기준: `TeamProject_Req1.pdf` 8, 11~14쪽. 아래 수치는 요구사항에서 지정하지 않은 구현 정책이다.

| 요구사항 | 구현 |
| --- | --- |
| 초기 자동 낙하: 1초에 한 칸 | Swing Timer, 초기 1,000ms |
| 생성 블록 수 또는 삭제 줄 수에 따라 가속 | 레벨 = `min(10, 1 + max(생성 수 / 10, 삭제 줄 수 / 10))` |
| 낙하 속도 증가 | 레벨마다 100ms 감소, 최소 100ms |
| 자동/수동 무관하게 낙하당 점수 | 실제 이동 1칸당 `1 + (레벨 - 1)`점. 하드 드롭도 동일 |
| 추가 점수 방식 1개 이상 | 한 번에 1/2/3/4줄 삭제 시 100/300/500/800 × 당시 레벨 |
| 일시정지/재개 | P 기본키, 타이머 중단, 이동 입력 무시, 재개 후 온전한 주기부터 시작 |
| 게임 중/일시정지 중 종료 | Esc 기본키 또는 `stop()`; `STOPPED`로 이동 |
| 더 쌓을 수 없는 경우 종료 | 생성 실패 또는 고정 시 `topOut` → `GAME_OVER`, 알림 1회 |
| 최소 세 가지 화면 크기 | SMALL 480×640, MEDIUM 600×800, LARGE 750×1000 |
| 색맹 모드 설정 | OFF / RED_GREEN / BLUE_YELLOW 저장. 색상 적용은 block/UI에서 수행 |
| 키 설정 | 좌/우/아래/회전/즉시 낙하/일시정지/종료, 중복키 금지 |
| 기본 설정 복원 | `SettingsRepository.reset()`으로 저장까지 완료 |
| 순위 영속화 및 초기화 | 기본 상위 10개, 설정 가능한 용량 ≥10, `ScoreboardRepository.reset()` |
| 새 기록 이름 입력/강조 지원 | `qualifies(score)`, `add(name, score)`가 반환하는 기록의 UUID |

첫 블록도 생성 수에 포함한다. 새 블록 생성 이후 레벨을 갱신하므로 10번째 블록부터 레벨 2이다.
낙하와 줄 삭제 점수는 그 동작을 수행한 레벨로 계산한다. 점수는 공용 이벤트의 `int` 범위를
초과하면 `Integer.MAX_VALUE`로 유지하여 음수가 되지 않는다. 벽에 막힌 이동, 회전, 0칸 낙하는
낙하 점수를 주지 않는다. 하드 드롭은 0칸이어도 블록을 고정한다.

## 담당자 1: 블록 연결 계약

`GameEngine.BoardDriver` 구현체를 `game` 패키지에 작성해 엔진 생성자에 전달한다.
`main`에는 블록 구현이 없으므로 `BoardDriver`를 주입받으며, 기본 생성 가능한 게임 보드는 제공하지 않는다.
2026-09-23에 원격 `feat/block`의 `63b1e21`까지 확인했으며, 아래에 실제 API의 대응 관계를 정리했다.
이 브랜치는 이번 작업에서 병합하지 않았다.

1. `reset()`은 보드/생성기/다음 블록 큐를 초기화한다. 현재 블록은 생성하지 않는다.
2. `spawnNextBlock()`은 큐의 블록을 현재 블록으로 만들고 큐를 채운다. 생성 충돌이면 `false`.
3. `moveLeft()`, `moveRight()`, `moveDown()`, `rotateClockwise()`는 성공 여부를 반환한다.
   `moveDown()`은 정확히 한 칸만 이동하며 실패해도 자체적으로 고정/생성하지 않는다.
4. `hardDrop()`은 마지막 유효 위치까지 이동하고 실제 이동 칸 수를 반환한다. 고정/생성하지 않는다.
5. `lockAndClearLines()`은 현재 블록을 한 번 고정하고 `LockResult(삭제 줄 수, topOut)`을 반환한다.
   보드 위쪽 초과는 `topOut=true`. 다음 블록 생성은 엔진이 한다.
6. `snapshot()`은 낙하 중 블록을 포함한 20×10 최종 화면이다. 빈칸은 `BoardSnapshot.EMPTY(-1)`.
7. `nextBlockTypes()`는 다음 블록 ID 배열이다. ID 0~6의 도형별 매핑은 담당자 1과 B가 맞춘다.

엔진은 UI에 전달할 때 보드 배열과 다음 블록 배열을 복사한다.

### feat/block API 대응 (63b1e21)

담당자 1의 `Board`는 **고정된 셀만** 보유한다. 따라서 `BoardDriver` 구현체가 현재 `Tetromino`,
`TetrominoGenerator`, 미리보기 큐를 소유한다. 어댑터는 담당자 2의 `game` 패키지에 둔다.

| BoardDriver 동작 | 실제 block API 연결 |
| --- | --- |
| 초기화 | `Board.clear()`와 함께 현재 블록/큐도 초기화, `generator.next()`로 큐 채우기 |
| 생성 | 큐에서 종류 선택 → `Tetromino(type, Rotation.SPAWN, row, col)` → `CollisionChecker.canPlace` |
| 좌/우/하강 | `current.moveBy(0, -1)`, `(0, 1)`, `(1, 0)` 후보를 충돌 검사 후 채택 |
| 회전 | `current.rotateClockwise()` 후보를 충돌 검사 후 채택; wall kick 없음 |
| 하드 드롭 | `DropCalculator.dropDistance(board, current)`만큼 이동하고 거리 반환 |
| 고정과 삭제 | `board.tryPlace(current)` → 현재 블록 해제 → `board.clearFullLines()` |
| 스냅샷 | `board.cellAt(row, col).map(TetrominoType::id).orElse(-1)`에 현재 블록의 `cells()` 합성 |
| 미리보기 | 큐의 `TetrominoType.id()` 배열; `ordinal()` 사용 금지 |

생성 위치는 실제 점유 폭으로 중앙 정렬하고 가장 위의 점유 셀이 0행에 오도록 한다.
I의 SPAWN 형태는 선행 빈 행이 있어 기준 행은 -1, 실제 점유 행은 0이다.
블록 ID는 `I=0, O=1, T=2, S=3, Z=4, J=5, L=6`이다.
이 블록 구현은 숨겨진 상단 행을 허용하지 않으므로 정상 플레이에서 `topOut` 대신
생성 후보의 충돌로 게임이 종료된다. 유효한 현재 블록의 `tryPlace` 실패는 어댑터 상태 오류이며
정상 게임 종료로 위장하지 않는다. 정상 플레이의 삭제 줄 수는 0~4이다.

현재 블록 모듈의 보조 팔레트는 `ColorScheme.colorBlind()` 한 가지이다.
UI는 `settings.isColorBlindModeEnabled()`로 일반/보조 팔레트를 선택할 수 있다.
저장된 RED_GREEN/BLUE_YELLOW 두 값은 현 팔레트에서는 동일하게 적용되며, 서로 다른 전용 팔레트를
제공한다는 뜻이 아니다. 적록·청황 모드별 실제 화면 식별성은 UI 통합 때 확인해야 한다.

## B: UI 연결

엔진 생성 이후 상태 조회/제어는 Swing EDT에서 수행한다. 기본 타이머도 EDT에서 동작하므로
UI 이벤트와 이동 처리가 동시에 보드를 수정하지 않는다. 콜백에서는 화면과 표시 값만 갱신한다.
콜백 안에서 엔진의 새 게임/종료 등을 다시 호출해야 하면 `SwingUtilities.invokeLater`로 예약한다.

```java
// EDT에서, boardDriver는 위 계약의 실제 구현, listener는 기존 GameEventListener 구현.
GameEngine engine = new GameEngine(boardDriver, listener);
engine.start();

// Swing InputMap/ActionMap의 WHEN_IN_FOCUSED_WINDOW에 설정 키를 등록하는 방식을 권장.
settings.keyBindings().actionFor(keyCode).ifPresent(engine::handle);
// 또는 ActionMap에서 engine.handle(GameAction.MOVE_LEFT) 등을 호출.
// 화면 크기: settings.screenSize().width(), settings.screenSize().height()
```

기본 키: ←/→ 이동, ↓ 한 칸 내리기, ↑ 시계 방향 회전, Space 즉시 낙하, P 중단/재개,
Esc 게임 종료. 이동 키 반복 입력은 매번 처리한다. P/Esc는 UI에서 키를 뗄 때까지 중복 실행을
막으면 길게 눌렀을 때 반복 토글/화면 이동을 방지할 수 있다. 키 변경 시 InputMap을 다시 등록한다.

`onBoardUpdated`, `onScoreChanged`, `onNextBlocksChanged`로 표시를 갱신하고,
`onPauseStateChanged`로 일시정지 화면을 전환한다. `onGameOver(finalScore)`에서는 다음을 진행한다.

1. `scoreboard.qualifies(finalScore)`를 확인한다.
2. 등재 가능하면 이름을 입력받고 `scoreboard.add(name, finalScore)`를 호출한다.
3. 반환된 `Optional<ScoreRecord>`가 있으면 `id()`를 보관한다.
4. `scoreboard.load()`로 점수 내림차순 목록을 표시하고 같은 ID인 행을 강조한다.
5. 처리 후 시작 메뉴로 돌아가거나 프로그램을 종료한다.

등재 조건: 빈자리가 있거나 최하위 점수보다 **엄격히 높은** 점수. 동점은 먼저 저장한 기록이
우선한다. 이름은 앞뒤 공백 제거 후 1~20 유니코드 코드 포인트이며 제어 문자를 금지한다.

사용자 Esc 종료는 `STOPPED`가 되며 `onGameOver`를 발생시키지 않는다. `handle(QUIT)` 또는
`stop()`의 성공 결과를 보고 UI가 메뉴로 이동한다. 화면을 떠날 때 `close()`로 타이머를 정리한다.

## 저장 경로와 오류 처리

기본 경로는 사용자 홈의 `.tetris-team-se8/settings.properties`, `scores.properties`이다.
현재 작업 디렉터리가 바뀌어도 같은 파일을 읽는다. 테스트/다른 배포에서는 생성자의 `Path`로 변경한다.
파일 내용은 UTF-8 properties이다. 점수 기록은 버전 1, 설정의 현재 저장 버전은 2이며
기존 버전 1 설정도 읽는다. 외부 JSON 라이브러리가 필요하지 않다.

설정 v1에 저장된 원래 일곱 동작의 키는 그대로 유지한다. 이후 새 `GameAction`을 추가할 때는
`KeyBindings.defaults()`에 기본키를 배정한다. 옛 파일에 그 키가 없어도 로드 중 새 동작만
기본값으로 채운다. 새 기본키가 사용자의 기존 키와 겹치면 사용자의 키를 우선하고,
새 동작에 사용하지 않은 F1~F12(그다음 A~Z)를 배정한다. v1에 원래 존재해야 하는 키가
누락된 경우는 손상된 파일로 취급한다. `ORIGINAL_ACTIONS` 목록은 새 동작을 추가해도
변경하지 않는다. 데이터 형식이 더 바뀔 때는 새 버전과 변환 규칙을 추가한다.

파일이 없거나 비어 있으면 기본 설정/빈 순위를 반환한다. 존재하는 데이터의 버전, 필수 값,
키 중복, 음수 점수 등이 잘못되면 `IOException`으로 알리고 읽기 과정에서 덮어쓰지 않는다.
UI는 저장 실패를 사용자에게 표시하고 재시도할 수 있게 한다. 사용자 선택에 의한 `reset()`은
손상된 파일도 새 기본값/빈 목록으로 교체한다. 설정 복원과 순위 삭제는 별도 동작이다.

저장은 동일 폴더의 임시 파일을 완성한 뒤 원본을 교체한다. 원자적 이동을 지원하지 않는 파일
시스템에서는 일반 교체로 전환한다. 저장소별 메서드는 한 인스턴스 내에서 직렬화한다.
앱 내에서 저장소 인스턴스 하나씩을 공유한다. 여러 앱 프로세스의 동시 쓰기 잠금은 제공하지 않는다.
디스크 작업은 `SwingWorker` 등에서 실행하고 완료 결과만 EDT로 전달하면 게임/화면이 멈추지 않는다.

## 검증

```powershell
.\gradlew.bat clean build
```

Windows의 한글 체크아웃 경로에서 테스트 클래스를 찾지 못하던 문제를 재현하고 수정했다.
Gradle JVM은 `gradle.properties`의 `-Dfile.encoding=COMPAT`으로 시스템 문자셋에 맞춰
테스트 실행용 인수 파일을 작성하고, `build.gradle`의 JavaCompile은 UTF-8을 명시한다.
게임 설정/순위 파일도 코드에서 UTF-8을 명시하므로 시스템 문자셋에 따라 저장 형식이 바뀌지 않는다.
이 변경 후 위 명령을 별도 인코딩 옵션 없이 검증했다.

JUnit 5로 상태/낙하/점수/속도 증가, 실제 Swing 타이머, 키 반복과 일시정지 중 입력,
종료/재시작 뒤 남은 타이머 무시, 한글 저장, 재로드, 동점 및 상위 10개, 초기화, 손상 데이터와
I/O 실패를 확인한다. 테스트는 `@TempDir`을 사용해 실제 사용자 설정/기록을 변경하지 않는다.
리포트: `build/reports/tests/test/index.html`, `build/reports/jacoco/test/html/index.html`.
블록 어댑터 테스트는 가짜 보드를 사용하므로 실제 충돌/회전/줄 삭제의 검증은 담당자 1 테스트와
통합 후 수행한다. 프로젝트의 기존 D 담당 NFR 테스트는 비활성 placeholder 상태로 유지했다.
