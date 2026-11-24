# 로컬 대전 화면 구현 인계서 (UI 담당 · AI 에이전트용)

이 문서 하나로 로컬 대전의 정식 화면을 구현할 수 있게 썼다. 대전 규칙·키 입력·키 저장·조립·화면 알림은 이미 구현되어 있고, **남은 일은 화면뿐**이다.
더 자세한 배경이 필요할 때만 링크된 문서를 연다.

- 기준 코드: PR #70(대전 규칙), PR #73(입력·키 저장·확인용 화면), 연출 알림 PR(#68 엔진 부분 + 하드드롭 알림)이 `main`에 들어간 상태.
- 작성: 로컬 대전 담당. 2026-11-24.

---

## 0. 에이전트에게 주는 작업 지시 요약

1. 아래 1절의 화면 5개와 메뉴 항목을 `ui` 패키지에 만든다. 2절의 작업 순서를 따른다.
2. 대전 규칙과 입력은 **새로 만들지 말고** 4절의 API를 호출만 한다. `game`, `common`, `block`, `settings` 패키지의 동작을 바꾸지 않는다.
3. 1인 모드의 모양과 연출(`GameScreen`, `docs/design/design-spec.md`)을 그대로 따른다.
4. 기존 테스트 파일은 고치지 않는다. 새 화면마다 테스트를 더한다(7절). 커밋마다 `./gradlew build`가 통과해야 한다.
5. 정해지지 않은 것(9절)은 임의로 정하지 말고 표시해 둔 기본안을 쓰고, PR 설명에 적는다.

---

## 1. 만들 것

| # | 화면/작업 | `ScreenRouter` 이름 (제안) | 클래스 (제안) | 요구사항 |
| --- | --- | --- | --- | --- |
| 1 | 보드 그리기 부품 분리 | — | `ui/BoardPanel` 등 | 대전에서 보드 2개를 1인 모드와 같게 그리기 위한 준비 |
| 2 | 대전 화면 | `VERSUS` | `ui/VersusScreen` | LB-F03, F06, F08, LB-I02, I04, I08 |
| 3 | 게임 방식 선택 화면 | `BATTLE_SELECT` | `ui/BattleSelectScreen` | LB-F02, LB-I06 |
| 4 | 승자 화면 | `VERSUS_RESULT` | `ui/VersusResultScreen` | LB-F14, LB-I07 |
| 5 | 대전 일시정지·나가기 | (일시정지 화면 재사용 또는 대전용) | — | LB-I03 |
| 6 | 시작 메뉴 "대전 모드" 항목 + `Main` 등록 | — | `MainMenuScreen`, `Main` | LB-F01 |
| 7 | 설정 화면의 대전 키 영역 | — | `SettingsScreen` | LB-I11 |
| 8 | 확인용 화면 정리 | — | `VersusCheckScreen` | — |

화면 흐름:

```text
시작 메뉴 ─▶ 게임 방식 선택 ─▶ 대전 화면 ─▶ 승자 화면
              ▲   │Esc            │Esc(확인)      ├ 다시 대전 ─▶ 대전 화면(같은 방식)
              │   ▼               ▼              ├ 방식 선택 ─▶ 게임 방식 선택
              └─ 시작 메뉴 ◀──────┘              └ 메인 화면으로 ─▶ 시작 메뉴
```

목업: `docs/design/mockups/BattleSelectScreen.png`, `VersusScreen.png`, `VersusAttack.png`, `PauseScreen.png`.

---

## 2. 작업 순서와 단계별 완료 기준

각 단계를 커밋 하나(또는 둘)로 나눈다. 커밋 메시지는 `종류(ui): 한국어 요약` 형식이다.

### 단계 1. 보드 그리기 부품 분리 (1인 모드 동작 그대로)

- 현재 `ui/GameScreen.java`(1,153줄)가 보드·셀 그리기, 줄 삭제 팝, 하드드롭 잔상, 고스트, 아이템 토스트를 엔진 하나에 묶어 갖고 있다. 대전에서는 같은 그리기가 플레이어마다 하나씩 필요하다.
- 보드 영역의 그리기와 연출 상태(줄 삭제 애니메이션, 하드드롭 잔상, 고스트)를 `GameEventListener`를 받는 부품으로 떼어 낸다. `GameScreen`은 그 부품을 하나 쓰고, 대전 화면은 둘 쓴다.
- 하드드롭 연출의 시작 방식을 바꾼다. 지금은 `GameScreen.handleAction`이 키를 직접 처리하면서 출발·도착 위치를 잡는다(`GameScreen.java` 466~469행). 대전에서는 키가 화면을 거치지 않으므로, **엔진 알림 `onHardDropped(before, after)`로 시작**하게 바꾼다. 1인 모드도 같은 알림을 쓰면 코드가 하나로 합쳐진다.
- 완료 기준: 1인 모드의 모양과 연출이 이전과 같다. 기존 `GameScreenTest`, `PauseScreenTest` 등 UI 테스트가 수정 없이 통과한다.

### 단계 2. 대전 화면 `VersusScreen`

배치(`docs/battle/screens.md` 3절):

```text
┌─────────────────────────────────────────────────────────────────────┐
│ 대전 모드 — 일반                                             02:31  │
│ ┌────────────┐  P1                             P2  ┌────────────┐   │
│ │            │  NEXT          ┌────┐         NEXT  │            │   │
│ │   P1 보드  │  SCORE         │ VS │        SCORE  │   P2 보드  │   │
│ │   20 × 10  │  LEVEL         └────┘        LEVEL  │   20 × 10  │   │
│ │            │  공격 대기열            공격 대기열 │            │   │
│ └────────────┘                                     └────────────┘   │
│  P1 W A S D · 왼쪽 Shift    P 일시정지 · Esc 나가기     P2 ←↑↓→ · Enter │
└─────────────────────────────────────────────────────────────────────┘
```

- 플레이어마다 보드, 다음 블럭, 점수, 레벨, 공격 대기열. 보드가 바깥쪽, 정보가 안쪽.
- 보드 테두리: P1 `UiColors.PLAYER1`, P2 `UiColors.PLAYER2`.
- 공격 대기열: 10줄×10칸 작은 격자. 위에서부터 대기 중인 줄을 빈 칸이 보이는 모양 그대로 회색(`CellPainter.drawGarbage`)으로 그리고, 줄 수(`4/10`)를 적는다.
- 보드의 회색 칸: `CellCodes.isGarbage(code)`이면 `CellPainter.drawGarbage`. **`isValid`보다 먼저 확인한다**(회색 코드는 `isValid`가 false다).
- 남은 시간: 시간제한 방식에서만 오른쪽 위에 `mm:ss`.
- 하단 키 안내: 현재 설정값으로 그린다(`settings.versusKeys().keyFor(player, action)`, 일시정지·나가기는 `settings.keyBindings().keyFor(PAUSE/QUIT)`). 대전 키는 `SidedKey`라서 "왼쪽 Shift"처럼 쓴다. `VersusCheckScreen.versusKeyName(int)`을 그대로 옮겨 쓰면 된다.
- 연출: 5절의 표대로 알림을 받아 1인 모드와 같은 연출을 플레이어 보드에 그린다.
- 화면 갱신은 알림이 온 플레이어 영역만 한다(성능 LB-N01).
- 키 입력: 3·4절의 `VersusInput`에 넘긴다. **키 위치(`getKeyLocation()`)를 반드시 함께 넘긴다.**
- 창 크기: 들어갈 때 넓히고 나갈 때 설정 크기로 되돌린다(아래 표, `router.updateWindowSize`).

| 설정 | 1인 창 | 대전 창 (기본안) |
| --- | --- | --- |
| SMALL | 480 × 640 | 1024 × 640 |
| MEDIUM | 540 × 720 | 1152 × 720 |
| LARGE | 600 × 800 | 1280 × 800 |

- 완료 기준: `--versus=normal`로 띄운 확인용 화면에서 되는 것이 전부 정식 화면에서도 된다. 세 창 크기에서 잘리지 않는다. 색맹 모드에서 회색 줄이 구분된다.

### 단계 3. 게임 방식 선택 `BattleSelectScreen`

- 카드 3개 세로 배치: 일반 대전 / 아이템 대전 / 시간제한 대전(제한 시간 `GameConstants.VERSUS_TIME_LIMIT_SECONDS` = 180초 → "3분"). 카드마다 한 줄 설명.
- `↑` `↓` 이동, `Enter` 시작, `Esc` 시작 메뉴. 하단에 사용 키 안내. 카드 아래에 P1·P2 키 셋(현재 설정값).
- `Enter`: `match.start(VersusMode.NORMAL | ITEM | TIME_LIMIT)` 호출 후 `VERSUS` 화면으로.
  - 순서 주의: 대전 화면의 `onShow`에서 `start`를 부를지, 선택 화면에서 부를지 한 곳으로 정한다. 확인용 화면은 `onShow`에서 부른다.

### 단계 4. 승자 화면 `VersusResultScreen`

- `VersusListener.onMatchEnded(MatchResult)`를 받으면 두 보드를 멈춘 상태로 잠시(기본안 1초, 1회성 타이머) 보여 준 뒤 이 화면으로 간다.
- 표시: `"P1 승리"` / `"P2 승리"` / `"무승부"`(`result.isDraw()`), 사유(`TOP_OUT` → "상대가 더 쌓을 수 없음", `TIME_UP` → "시간 종료"), 두 점수(`p1Score()`, `p2Score()`).
- 버튼 세 개: 다시 대전(`match.start(match.mode())` → `VERSUS`) / 방식 선택(`BATTLE_SELECT`) / 메인 화면으로(`MAIN_MENU`). `←` `→` 이동, `Enter` 선택.
- 이름 입력·스코어보드로 가는 길은 없다(LB-F15). 대전 결과는 저장하지 않는다.

### 단계 5. 대전 일시정지·나가기

- 일시정지 키(1인용 설정의 `PAUSE`, 기본 `P`): `match.pause()` 후 일시정지 화면을 덮는다. 재개는 `match.resume()`.
  - 현재 `PauseScreen`은 생성자가 `GameEngine` 하나를 받고 `ScreenRouter.GAME`으로 돌아가게 고정되어 있다(`PauseScreen.java` 43·52·96·123행). 대전에서도 쓰려면 "멈춤/재개/나가기 동작"과 "돌아갈 화면"을 주입받게 일반화하거나, 대전용 일시정지 화면을 따로 만든다. 기존 `PauseScreenTest`가 통과해야 한다.
- 나가기 키(1인용 설정의 `QUIT`, 기본 `Esc`): `match.pause()` → 1인 모드와 같은 문구·기본 선택의 확인 대화상자(`ThemedDialog.showOptions`, "메인 화면으로 나갈까요?…", 기본 "계속하기") → 나가면 `match.stop()` 후 `MAIN_MENU`, 아니면 `match.resume()`.
- 대전 화면을 떠나거나(`onHide`) 창이 포커스를 잃으면 `input.releaseAll()`. 대전 화면을 떠날 때 진행 중이면 일시정지한다(`GameScreen.onHide`와 같은 동작).

### 단계 6. 시작 메뉴와 조립

- `MainMenuScreen`에 "대전 모드" 항목을 추가한다. P2P 담당도 "P2P 대전" 항목을 넣으므로 순서는 게임 시작 → 아이템 모드 → 대전 모드 → P2P 대전 → 설정 → 스코어보드 → 게임 종료(`integration.md` 3절). 현재 생성자는 `MainMenuScreen(ScreenRouter, Runnable startNormal, Runnable startItemMode)`라서 동작을 하나 더 받게 넓힌다. 기존 `MainMenuScreenTest`·`MainMenuItemModeTest`가 통과해야 한다.
- `Main.Application`에는 이미 `versusMatch`, `versusInput` 필드가 있고 설정이 바뀌면 `versusInput.setKeys(updated.versusKeyMap())`를 부른다(8절). 새 화면 셋을 여기서 만들어 `router.addScreen`으로 등록하기만 하면 된다.

### 단계 7. 설정 화면의 대전 키 영역

- `SettingsScreen`의 조작 키 영역에 "1인용 / 대전 P1 / 대전 P2" 선택을 두고, 고른 대상의 키 목록을 같은 자리에 보여 준다. 키 줄을 열 개 더 늘어놓으면 SMALL 창에 안 들어간다.
- 대전 P1·P2를 고르면 다섯 줄(`VersusKeyBindings.PLAYER_ACTIONS` 순서)만 보이고 일시정지·나가기 줄은 숨긴다.
- 키를 받을 때 **`SidedKey.of(event.getKeyCode(), event.getKeyLocation())`**로 값을 만든다. 그래야 왼쪽·오른쪽 Shift가 구분되어 저장된다.
- 저장: `settings.withVersusKeys(newKeys)`. 대전 키끼리 겹치거나 일시정지·나가기 키와 겹치면 `IllegalArgumentException` → 기존 안내("이미 다른 동작에 쓰이는 키입니다." 또는 현재 쓰는 문구)를 띄우고 받지 않는다.
- 1인용 일시정지·나가기 키를 대전 키와 같게 바꾸는 것은 막지 않는다. `settings.versusKeyClashes()`가 비어 있지 않으면 경고 문구를 보여 주면 된다.
- 저장은 기존처럼 `SettingsRepository.save(settings)` 한 번이면 대전 키도 함께 저장된다. "기본값 되돌리기"도 대전 키를 포함한다.

### 단계 8. 확인용 화면 정리

- 정식 화면이 다 들어오면 `ui/VersusCheckScreen`과 `Main`의 `--versus` 실행 인자를 지울지, 개발용으로 남길지 로컬 대전 담당과 정한다. 지운다면 `VersusCheckScreenTest`, `MainArgumentsTest`도 함께 지운다.

---

## 3. 이미 있는 것 (만들지 않는다)

| 무엇 | 어디 | 비고 |
| --- | --- | --- |
| 대전 한 판 (엔진 2개·공격·승패·일시정지·제한 시간·아이템 방식) | `game/versus/VersusMatch` | 화면은 이것만 상대한다 |
| 대전 알림 | `game/versus/VersusListener` | 대기열·레벨·남은 시간·일시정지·결과 |
| 결과 값 | `game/versus/MatchResult` | |
| 2인 키 분배와 누름 반복 | `game/versus/VersusInput` | 키 코드·위치만 받는다 |
| 대전 키 값과 좌우 구분 | `settings/VersusKeyBindings`, `game/versus/SidedKey` | 기본 P1 A·D·S·W·왼쪽 Shift / P2 방향키·Enter |
| 대전 키 저장 | `settings/SettingsRepository` | `versus.*` 줄 |
| 조립 | `Main.Application` | `versusMatch`, `versusInput` |
| 연출 알림 | `common/events/GameEventListener` | 하드드롭·폭탄·무게추 포함 (5절) |
| 동작하는 예제 화면 | `ui/VersusCheckScreen` | 꾸미지 않음. 연결 방법을 그대로 보여 준다 |

확인용 화면 실행: `./gradlew run --args="--versus=normal"` (`item`, `time`).

---

## 4. 연결 지점 API (정확한 시그니처)

모든 호출과 알림은 **Swing EDT**에서 일어난다.

### 4.1 `com.team.tetris.game.versus.VersusMatch`

```java
public enum State { READY, RUNNING, PAUSED, ENDED }

void setListener(VersusListener listener);                         // 대전 알림, 한 곳만
void setPlayerListener(Player player, GameEventListener listener); // 플레이어별 보드·점수·연출 알림

void start(VersusMode mode);   // NORMAL / ITEM / TIME_LIMIT. 진행 중이던 판은 결과 없이 버리고 새 판
boolean pause();               // 두 보드와 제한 시간을 함께. 상태가 안 맞으면 false
boolean resume();
boolean stop();                // 승패 없이 끝, 상태 READY, 결과 알림 없음
void close();                  // 프로그램 종료 때

State state();
VersusMode mode();
int remainingSeconds();        // 시간제한만. 다른 방식은 0
int score(Player player);
int level(Player player);
List<GarbageLine> queue(Player player);   // 위에서 아래 순서

boolean handle(Player player, GameAction action);   // VersusInput이 부른다. 화면은 직접 부르지 않는다
```

- `Player`(`common.battle`): `P1`(왼쪽), `P2`(오른쪽), `opponent()`.
- `VersusMode`(`common.battle`): `NORMAL`, `ITEM`, `TIME_LIMIT`.

### 4.2 `com.team.tetris.game.versus.VersusListener` (모두 빈 기본 구현)

| 알림 | 언제 |
| --- | --- |
| `onQueueChanged(Player, List<GarbageLine>)` | 시작할 때(빈 목록), 공격을 받았을 때, 보드에 들어가 비워졌을 때(빈 목록) |
| `onLevelChanged(Player, int level)` | 시작할 때 한 번, 레벨이 바뀔 때 |
| `onTimeChanged(int remainingSeconds)` | 시간제한만. 시작할 때(180), 이후 1초마다. 일시정지 중에는 오지 않음 |
| `onPauseChanged(boolean paused)` | `pause()`·`resume()`, 일시정지 중 `stop()`(false) |
| `onMatchEnded(MatchResult)` | 승패가 났을 때 한 판에 한 번. `stop()`으로 끝나면 오지 않음 |

`GarbageLine`(`common.battle`): `filled(int col)` — 그 열(왼쪽 0)이 차 있는지. `GarbageLine.COLS` = 10.

`MatchResult`(record): `winner()`(무승부면 null), `winnerIfAny()`, `isDraw()`, `reason()`(`TOP_OUT` / `TIME_UP`), `p1Score()`, `p2Score()`, `scoreOf(Player)`.

### 4.3 플레이어별 `GameEventListener` (1인 모드 화면과 같은 알림)

| 알림 | 내용 |
| --- | --- |
| `onBoardUpdated(BoardSnapshot)` | 20×10 보드(낙하 블럭 포함). 회색 칸 코드는 `CellCodes.GARBAGE` |
| `onLandingUpdated(falling, landing)` | 낙하 블럭과 착지 예고(고스트) |
| `onLinesCleared(rows, boardBeforeClear)` | 줄 삭제 |
| `onScoreChanged(int)`, `onNextBlocksChanged(int[])` | 점수, 다음 블럭 |
| `onFeverActivated(int seconds)`, `onSlowDownActivated(prev, curr)` | 아이템 효과 |
| `onHardDropped(BoardSnapshot before, BoardSnapshot after)` | 하드드롭. 떨어지기 전·후 낙하 블럭 칸만 담은 보드. 고정·줄 삭제 알림보다 먼저 |
| `onBombExploded(CellPosition center, List<CellPosition> blastCells)` | 폭탄 폭발. 중심과 3×3 범위(보드 밖·이미 꽉 찬 행 제외, 빈 칸 포함). 줄 삭제 알림보다 먼저 |
| `onAnvilCrushed(List<CellPosition> crushedCells)` | 무게추가 부순 칸. 한 칸 낙하마다, 하드드롭이면 한 번에 모아서 |
| `onPauseStateChanged`, `onGameOver` | 플레이어별로 오지만 화면 전환에는 쓰지 않는다. 대전은 4.2의 알림으로 |

`CellPosition`(`common.events`): `row()`(0이 맨 위), `col()`(0이 맨 왼쪽).

### 4.4 `com.team.tetris.game.versus.VersusInput`

```java
boolean keyPressed(int keyCode, int location);   // 대전 조작 키를 썼으면 true. 진행 중이 아니면 false
void keyReleased(int keyCode, int location);
void releaseAll();                               // 화면을 떠날 때, 포커스를 잃을 때, 대화상자를 띄울 때
void setKeys(Map<Integer, PlayerMove> keys);     // Main이 설정 변경 때 부른다. 화면은 부르지 않는다
```

화면에서 키를 넘기는 코드(`onShow`에서 등록, **`onHide`에서 반드시 해제**):

```java
private final KeyEventDispatcher forward = event -> switch (event.getID()) {
    case KeyEvent.KEY_PRESSED -> input.keyPressed(event.getKeyCode(), event.getKeyLocation())
            || handleScreenKey(event.getKeyCode());   // 일시정지·나가기 등 화면의 키
    case KeyEvent.KEY_RELEASED -> {
        input.keyReleased(event.getKeyCode(), event.getKeyLocation());
        yield false;
    }
    default -> false;
};
// onShow: KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(forward);
// onHide: removeKeyEventDispatcher(forward); input.releaseAll();
```

- 일시정지·나가기 키는 `VersusInput`이 처리하지 않는다. 화면이 받는다.
- 디스패처를 떼지 않으면 다른 화면의 Enter·P·Esc를 가로챈다(실제로 테스트에서 겪었다).
- OS 자동 반복을 걸러 내지 않고 그대로 넘긴다. 반복은 `VersusInput`이 키마다 직접 돌린다.

### 4.5 설정 (`com.team.tetris.settings`)

```java
VersusKeyBindings GameSettings.versusKeys();
GameSettings GameSettings.withVersusKeys(VersusKeyBindings keys);   // 일시정지·나가기 키와 겹치면 IllegalArgumentException
Set<GameAction> GameSettings.versusKeyClashes();                    // 겹친 공용 동작 (경고용)
Map<Integer, PlayerMove> GameSettings.versusKeyMap();               // VersusInput용

Set<GameAction> VersusKeyBindings.PLAYER_ACTIONS;   // MOVE_LEFT, MOVE_RIGHT, SOFT_DROP, ROTATE_CLOCKWISE, HARD_DROP
Map<GameAction, Integer> keys(Player player);
int keyFor(Player player, GameAction action);       // SidedKey 값
VersusKeyBindings withKey(Player player, GameAction action, int key);   // 대전 키끼리 겹치면 IllegalArgumentException
static VersusKeyBindings defaults();
```

`SidedKey`(`game.versus`): `of(keyCode, location)`, `left(keyCode)`, `right(keyCode)`, `keyCode(key)`, `side(key)`(`ANY` / `LEFT` / `RIGHT`).

---

## 5. 연출별 신호

`design-spec.md` §3·§4의 연출을 대전 보드에도 그린다. 구현 방식은 스펙 §3대로 "이벤트 시각을 필드에 기록 → 그릴 때 `now - t < duration`이면 그림"이고, 1회성 타이머만 쓴다.

| 연출 | 스펙 | 신호 | 대전에서 |
| --- | --- | --- | --- |
| 줄 삭제 팝 | §3.1 | `onLinesCleared(rows, boardBeforeClear)` | 옴 |
| 고스트 | §2 | `onLandingUpdated` | 옴 |
| 하드드롭 잔상·눌림·흔들림 | §3.3 | `onHardDropped(before, after)` | 옴 (새 알림) |
| 피버·속도 감소 토스트 | §4 | `onFeverActivated`, `onSlowDownActivated` | 옴 |
| 폭탄 3×3 accent 점선 사각형 1프레임 | §4 `B` | `onBombExploded(center, blastCells)` | 옴 (새 알림, 이슈 #68) |
| 무게추 연기 퍼프 | §4 `W` | `onAnvilCrushed(crushedCells)` | 옴 (새 알림, 이슈 #68). **모양 미정 — 9절** |
| 공격 발생 글로우, 대기열 적재 플래시, 회색 뭉치 삽입 | §3.2 | 공격: 상대의 `onQueueChanged`에서 줄 수가 늘 때. 삽입: 자기 `onQueueChanged`가 줄이 있던 상태에서 빈 목록이 될 때(대전 시작 때의 빈 목록은 삽입이 아니다) | 옴 |

---

## 6. 지켜야 할 것

- **`design-spec.md` §7 제약**: 모든 셀에 문자, 색맹 모드에서도 구분(색+문자+표정), 상시 repaint 루프·배경 애니메이션 금지(연출은 1~2프레임 단발), S/M/L 세 창 크기에서 레이아웃 무결, 아이템 문자 L·B·F·S·W 고정.
- 셀 그리기는 `ui/theme/CellPainter`, 색은 `UiColors`, 글꼴은 `UiFonts`를 쓴다. 서체에 없는 글자(·, ←→↑↓)는 `UiFonts.withFallback`으로 그린다(확인용 화면 `VersusCheckScreen.text` 참고).
- 대화상자는 `ThemedDialog`.
- `game`·`common`·`block`·`settings` 패키지 코드를 바꾸지 않는다. 바꿔야 한다면 로컬 대전 담당과 먼저 맞춘다.
- `ui` 패키지는 `game`을 써도 되지만 `game`이 `ui`를 import하면 안 된다.
- 기존 테스트 파일은 고치지 않는다. 고쳐야 통과한다면 1인 모드 동작이 바뀐 것이다.
- 화면에서 엔진을 직접 만들지 않는다. `Main`이 만든 `versusMatch`·`versusInput`을 생성자로 받는다.

---

## 7. 테스트

- UI 테스트는 창이 필요하다. 클래스 머리에 `assumeFalse(GraphicsEnvironment.isHeadless(), ...)`를 둔다(기존 `ScoreboardScreenTest` 방식). CI는 Windows·macOS 러너라 실행된다.
- 대전을 테스트에서 만드는 법: `new VersusMatch(setup, clock)`에 블록 순서·타이머를 고정한 `PlayerSetup`을 넣는다. `ui/VersusCheckScreenTest`의 `Fixture`를 그대로 가져다 쓰면 된다(O만 나오고 자동 낙하 없음, 제한 시간은 테스트가 직접 넘김).
- 키는 실제 이벤트 대신 화면의 키 처리 메서드를 직접 부른다(확인용 화면의 `keyPressed(int keyCode, int location)` 패턴).
- 그리기 확인은 `BufferedImage`에 `screen.paint(g)`로 그려 본다. 화면이 `KeyEventDispatcher`를 등록했다면 테스트 끝에 반드시 화면을 떠나게(`router.showScreen(다른 화면)`) 해서 디스패처를 뗀다.
- 각 화면에서 최소한 확인할 것:
  - 대전 화면: 들어가면 창이 넓어지고 대전이 시작됨, 나가면 원래 크기, 키가 `VersusInput`으로 감, 일시정지 키, 모든 상태(진행·일시정지·시간제한·결과 직전) 그리기.
  - 방식 선택: `↑` `↓` `Enter`로 각 방식이 시작됨, `Esc`로 시작 메뉴.
  - 승자 화면: 승자·무승부·사유·점수 표시, 세 버튼의 이동 대상.
  - 설정: 대전 키 바꾸기·저장·겹침 거부, 왼쪽·오른쪽 Shift 구분 저장.
- 실행: `./gradlew build` (테스트 + 커버리지 50% 기준). JDK 21.

---

## 8. 현재 `Main` 조립 (참고)

```java
// Main.Application 필드 (이미 있음)
private final VersusMatch versusMatch = new VersusMatch(VersusMatch.standard(items, () -> settings.difficulty()));
private final VersusInput versusInput = new VersusInput(versusMatch, settings.versusKeyMap());

// applySettings (이미 있음)
versusInput.setKeys(updated.versusKeyMap());

// 창 닫을 때 (이미 있음)
versusMatch.close();
```

새로 할 일: 화면 셋을 만들고 `router.addScreen(...)`, 시작 메뉴 항목의 동작을 `router.showScreen(BATTLE_SELECT)`로 연결. `VersusMatch.setListener`는 한 곳만 받으므로, 대전 화면이 받고 결과를 승자 화면에 넘기는 식으로 한 곳에서 나눠 준다. 지금은 확인용 화면이 `setListener`를 부르므로, 정식 화면을 등록할 때 확인용 화면은 등록하지 않거나(실행 인자가 없을 때 이미 등록 안 됨) 정리한다.

---

## 9. 정해지지 않은 것 (기본안으로 진행하고 PR에 적는다)

| 항목 | 기본안 | 정할 사람 |
| --- | --- | --- |
| 무게추 "연기 퍼프"의 모양·색·지속 시간 | 부서진 칸 위치에 `UiColors.STEEL_HIGHLIGHT` 반투명 원 2~3개를 120ms 동안 | UI 담당(디자인) |
| 무게추 하드드롭 때의 퍼프 | 부서진 칸 전부에 한 번 | UI 담당 |
| 폭탄 점선 굵기·간격 | 고스트와 같은 2px 점선 | UI 담당 |
| 대전 창 크기 | 2절 단계 2의 표 | UI 담당 |
| 방식 선택·승자 화면도 넓은 창으로 보일지 | 대전 화면에서만 넓힘 | UI 담당 |
| 일시정지 화면: 기존 `PauseScreen` 일반화 vs 대전용 새 화면 | 일반화 (돌아갈 화면과 동작을 주입) | UI 담당 |
| 확인용 화면을 남길지 | 정식 화면 머지 후 지움 | 로컬 대전 담당과 함께 |

---

## 10. 완료 체크리스트

- [ ] 시작 메뉴 → 대전 모드 → 방식 선택 → 대전 → 승자 화면 → (다시 대전 / 방식 선택 / 메인) 흐름이 키보드만으로 된다
- [ ] 두 보드가 1인 모드와 같은 셀·연출로 그려진다 (줄 삭제, 고스트, 하드드롭, 아이템 문자, 폭탄·무게추 연출)
- [ ] 공격 대기열 격자에 빈 칸 모양이 보이고, 다음 블럭 직전에 보드로 들어간다
- [ ] 시간제한에서 `mm:ss`가 줄고, 일시정지 중에는 멈춘다
- [ ] 일시정지·재개·나가기(확인 대화상자)가 두 보드에 함께 적용된다
- [ ] S/M/L 세 크기에서 잘리지 않고, 대전을 나오면 원래 창 크기로 돌아온다
- [ ] 색맹 모드 두 종류에서 회색 줄과 블럭이 구분된다
- [ ] 설정 화면에서 대전 P1·P2 키를 바꾸고 저장·재실행 후 유지된다. 왼쪽·오른쪽 Shift가 구분된다
- [ ] 대전 뒤 1인 모드·스코어보드가 이전과 같다. 대전 결과는 스코어보드에 없다
- [ ] 기존 테스트 수정 없이 `./gradlew build` 통과, 새 화면 테스트 추가

## 참고 문서

- `docs/battle/ui-contract.md` — 연결 지점 계약 원문과 담당 경계
- `docs/battle/screens.md` — 화면 요구사항 원문
- `docs/battle/input.md` — 키 입력·대전 키 설정
- `docs/battle/rules.md` — 대전 규칙
- `docs/design/design-spec.md` — 색·셀·연출·제약
- `docs/battle/test-plan.md` 3절 — 수동 확인 항목과 결과
