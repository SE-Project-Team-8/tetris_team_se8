# UI 연결 지점

로컬 대전 담당(윤성빈)은 화면을 만들지 않는다. 대전 로직과 **UI가 그 위에 화면을 얹을 수 있는 연결 지점**까지 만든다(`decisions.md` D-12).
이 문서는 UI 담당(조상윤·안성현)에게 넘기는 계약이다. P2P 담당(손동민)도 같은 연결 지점을 쓴다. 바로 작업에 들어갈 때는 `ui-handoff.md`(작업 순서·시그니처·완료 기준을 한 문서에 모은 인계서)를 본다.

> 2·4절은 PR 1(`feat/versus-core`)의 코드와 같다. 이름과 모양이 바뀌면 코드와 이 문서를 같은 커밋에서 고친다.
> 3절(`VersusInput`), 4.3절의 대전 키 값, 5절(확인용 화면)은 PR 2(`feat/versus-play`)에서 만들었다.
> **이 범위 나눔은 UI 담당의 동의가 필요하다.** 역할 배정 문서와 다르기 때문이다(1절).

## 1. 누가 무엇을 만드는가

| 구분 | 로컬 대전 (윤성빈) | UI (조상윤·안성현) |
| --- | --- | --- |
| 대전 규칙 | 공격 줄, 대기열, 보드 삽입, 승패, 시간제한 | — |
| 조립 | `Main`에서 엔진 2개와 `VersusMatch`를 만들어 연결 | 화면을 `ScreenRouter`에 등록 |
| 키 입력 | 키 분배와 누름 반복 (`VersusInput`) | 창의 키 이벤트를 `VersusInput`에 넘겨줌 |
| 대전 키 설정 | 값(`VersusKeyBindings`)과 설정 파일 저장 | 설정 화면의 대전 키 영역 |
| 화면 | **확인용 화면** 하나 (꾸미지 않음, 5절) | 시작 메뉴 항목, 방식 선택·대전·승자 화면, 창 크기 변경, 일시정지 화면 |
| 테스트 | 로직과 입력의 단위 테스트 | 화면 테스트 |

역할 배정 문서(2026-11-16)는 "메뉴·모드 선택", "대기열 표시 영역", "승자 화면 표시"를 로컬 대전 담당 항목에 적었다. 위 표는 그 화면들의 **그리기**를 UI 담당으로 옮기고, 로컬 대전 담당은 그 화면에 필요한 **데이터와 동작**을 제공하는 것으로 바꾼 것이다.

## 2. UI가 호출하는 것

`com.team.tetris.game.versus.VersusMatch` 하나만 상대한다. 모두 Swing EDT에서 호출한다(`GameEngine`과 같은 계약).

**만들기와 연결.** `VersusMatch`는 `Main`이 만들어 화면에 넘긴다(1절). 화면은 알림을 받을 곳만 등록한다.

```java
VersusMatch match = new VersusMatch(VersusMatch.standard(items, () -> settings.difficulty()));
match.setListener(versusListener);                  // 4.2 대전 알림. 한 곳만 둔다
match.setPlayerListener(Player.P1, leftBoardPanel);   // 4.1 플레이어별 표시 정보
match.setPlayerListener(Player.P2, rightBoardPanel);
```

- `VersusMatch.standard(items, difficulty)`: 실제 게임의 구성. 두 플레이어에 같은 아이템 목록과 설정의 난이도를 쓴다(D-11).
- 제한 시간은 Swing 타이머로 잰다. 테스트는 `new VersusMatch(setup, clock)`으로 직접 넘기는 타이머를 넣는다.

**진행.**

| 호출 | 언제 | 비고 |
| --- | --- | --- |
| `start(VersusMode mode)` | 방식 선택 화면에서 Enter, 승자 화면에서 "다시 대전" | `NORMAL` / `ITEM` / `TIME_LIMIT`. 진행 중이던 판은 결과 없이 버리고, 보드·점수·대기열·제한 시간을 처음으로 돌려 새 판을 시작 |
| `pause()` / `resume()` | 일시정지 키, 화면이 가려질 때 / 돌아올 때 | 두 보드와 제한 시간이 함께 멈춘다. 상태가 맞지 않으면 false |
| `stop()` | 나가기를 확인했을 때 | 승패 없이 끝나고 상태가 `READY`로 돌아간다. 결과 알림이 오지 않는다 |
| `close()` | 대전을 더 쓰지 않을 때(프로그램 종료) | 진행 중이면 `stop()`하고 타이머를 모두 멈춘다 |

**현재 값 읽기.** 알림을 기다리지 않고 처음 그릴 때 쓴다.

| 호출 | 값 |
| --- | --- |
| `state()` | `READY`(시작 전·중단 뒤) / `RUNNING` / `PAUSED` / `ENDED`(승패가 난 뒤) |
| `mode()` | 마지막으로 시작한 방식 |
| `remainingSeconds()` | 시간제한 대전의 남은 초. 다른 방식에서는 0 |
| `score(player)`, `level(player)` | 그 플레이어의 점수·레벨 |
| `queue(player)` | 그 플레이어의 공격 대기열. 위에서 아래 순서 |

블럭 조작 `handle(player, action)`은 UI가 직접 호출하지 않는다. 키 이벤트를 3절의 `VersusInput`에 넘기면 `VersusInput`이 호출한다. 진행 중일 때만 받고, `PAUSE`·`QUIT`를 넘기면 예외다(일시정지와 나가기는 위의 `pause()`·`stop()`).

## 3. UI가 키 이벤트를 넘기는 곳

`com.team.tetris.game.versus.VersusInput`. `Main`이 `new VersusInput(match, settings.versusKeyMap())`로 만들어 화면에 넘긴다.

`VersusInput`은 화면을 모르는 클래스다. 키 코드·위치의 눌림과 뗌만 받는다. 어느 키가 누구의 어떤 동작인지, 누르고 있을 때의 반복, 하드드롭 한 번만 실행 같은 처리는 전부 안에서 한다(`input.md`).

| 호출 | 언제 |
| --- | --- |
| `keyPressed(int keyCode, int location)` | 대전 화면이 보이는 동안 창에서 `KEY_PRESSED`를 받을 때마다 (OS 자동 반복 포함, 걸러 내지 않고 그대로) |
| `keyReleased(int keyCode, int location)` | `KEY_RELEASED`를 받을 때마다 |

`location`은 `KeyEvent.getKeyLocation()`이다. 왼쪽 Shift(P1)와 오른쪽 Shift(P2)를 가르는 데 쓴다(D-13).
| `releaseAll()` | 화면을 떠날 때, 창이 포커스를 잃을 때, 대화상자를 띄울 때 |

- 포커스가 어디에 있든 받도록 `KeyboardFocusManager`의 `KeyEventDispatcher`로 등록해 넘긴다(요구사항 p.7). `HeldKeys`, `PauseScreen`이 쓰는 방식과 같다.
- `keyPressed`는 그 키가 P1·P2의 조작 키여서 처리했으면 true를 돌려준다. 대전이 진행 중이 아니면(일시정지·종료) 조작 키도 false라서, 디스패처가 이벤트를 삼키지 않고 일시정지 화면 등에 넘긴다.
- 키 설정이 바뀌면 `Main`이 `input.setKeys(...)`로 알린다. 화면이 할 일은 없다.
- **일시정지 키와 나가기 키는 `VersusInput`이 처리하지 않는다.** 화면 전환과 확인 대화상자가 따르는 키라 UI가 직접 받아 `match.pause()`·`match.stop()`을 호출한다(1인 모드의 화면들이 하는 방식과 같다).

```java
// 대전 화면의 onShow / onHide 에서 (예시)
KeyEventDispatcher forward = event -> {
    switch (event.getID()) {
        case KeyEvent.KEY_PRESSED -> { return input.keyPressed(event.getKeyCode(), event.getKeyLocation()); }
        case KeyEvent.KEY_RELEASED -> input.keyReleased(event.getKeyCode(), event.getKeyLocation());
    }
    return false;
};
KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(forward);   // onShow
// onHide: removeKeyEventDispatcher(forward); input.releaseAll();
```

## 4. UI가 받는 것

### 4.1 플레이어별 표시 정보

플레이어(P1, P2)마다 기존 `GameEventListener`를 `match.setPlayerListener(player, listener)`로 하나씩 등록한다. 1인 모드 화면이 받는 것과 같은 알림이다.

| 알림 | 내용 |
| --- | --- |
| `onBoardUpdated(BoardSnapshot)` | 20×10 보드(낙하 블럭 포함). 받은 회색 칸의 코드는 `CellCodes.GARBAGE`이며 `CellCodes.isGarbage(code)`로 먼저 걸러 낸다 (`isValid`는 이 코드에 false다) |
| `onLandingUpdated(falling, landing)` | 낙하 블럭과 착지 예고 칸 |
| `onLinesCleared(rows, boardBeforeClear)` | 줄 삭제 표시용 |
| `onScoreChanged(int)` | 점수 |
| `onNextBlocksChanged(int[])` | 다음 블럭 |
| `onFeverActivated`, `onSlowDownActivated` | 아이템 효과 표시용 (아이템 대전) |
| `onHardDropped(before, after)` | 하드드롭 잔상·눌림 연출용. 떨어지기 전·후 낙하 블럭 칸. 고정·줄 삭제 알림보다 먼저 |
| `onBombExploded(center, blastCells)` | 폭탄 3×3 점선 연출용 (이슈 #68). 줄 삭제 알림보다 먼저 |
| `onAnvilCrushed(crushedCells)` | 무게추 연기 퍼프 연출용 (이슈 #68). 한 칸 낙하마다, 하드드롭이면 한 번에 |

1인 모드는 하드드롭 연출을 화면이 키를 직접 처리하며 시작하지만, 대전은 키가 `VersusInput`을 거쳐 엔진으로 바로 가므로 `onHardDropped`로 시작해야 한다. 연출별 신호 표는 `ui-handoff.md` 5절.

`onGameOver`와 `onPauseStateChanged`는 플레이어별로 오지만 화면 전환에는 쓰지 않는다. 승패와 일시정지는 4.2의 대전 알림으로 처리한다.

### 4.2 대전 알림

`com.team.tetris.game.versus.VersusListener`를 `match.setListener(listener)`로 하나 등록한다. 모든 메서드에 빈 기본 구현이 있어 필요한 것만 구현하면 된다.

| 알림 | 언제 | 내용 | 관련 요구사항 |
| --- | --- | --- | --- |
| `onQueueChanged(Player, List<GarbageLine>)` | 시작할 때(빈 목록), 공격을 받았을 때, 보드에 들어가 비워졌을 때(빈 목록) | 그 플레이어의 공격 대기열 전체. 위에서 아래 순서 | LB-F06 |
| `onLevelChanged(Player, int level)` | 시작할 때 한 번, 레벨이 바뀔 때 | 그 플레이어의 레벨 | LB-I02 |
| `onTimeChanged(int remainingSeconds)` | 시간제한 대전에서만. 시작할 때(180), 그 뒤 1초마다 | 남은 초. 일시정지 중에는 오지 않는다 | LB-I08 |
| `onPauseChanged(boolean paused)` | `pause()`·`resume()`, 일시정지 중의 `stop()`(false) | 대전 전체의 일시정지 여부 | LB-I03 |
| `onMatchEnded(MatchResult)` | 승패가 났을 때 한 판에 한 번. `stop()`으로 끝나면 오지 않는다 | 아래 표 | LB-F13, F14 |

`GarbageLine`의 `filled(col)`이 그 열(왼쪽이 0)이 차 있는지 알려 준다. 대기열 격자는 줄마다 이것으로 10칸을 그린다.

`MatchResult`(record)의 값.

| 메서드 | 값 |
| --- | --- |
| `winner()` | 승자 `Player`. 무승부면 null. `winnerIfAny()`는 `Optional`, `isDraw()`는 무승부 여부 |
| `reason()` | `TOP_OUT`(한쪽이 더 쌓을 수 없음) / `TIME_UP`(제한 시간 끝). 무승부는 `TIME_UP`에서만 나온다 |
| `p1Score()`, `p2Score()`, `scoreOf(player)` | 끝난 시점의 두 점수 |

### 4.3 그릴 때 필요한 값

| 값 | 어디서 |
| --- | --- |
| 칸의 색·문자 | 기존 `CellCodes`, `ColorScheme`. 회색 칸은 `CellCodes.GARBAGE`이고 `UiColors.GARBAGE`·`GARBAGE_GLYPH`로 그린다 |
| 화면에 안내할 대전 키 | `settings.versusKeys().keyFor(player, action)` (`VersusKeyBindings`). 값은 `SidedKey`라서 `SidedKey.keyCode`·`side`로 "왼쪽 Shift"처럼 쓴다(`VersusCheckScreen.versusKeyName` 참고) |
| 일시정지·나가기 키 | 1인용 `KeyBindings`의 `PAUSE`, `QUIT` |
| 제한 시간 | `GameConstants.VERSUS_TIME_LIMIT_SECONDS` (180) |
| 대기열 상한 | `GameConstants.ATTACK_QUEUE_MAX_LINES` (10). `GarbageQueue.CAPACITY`와 같다 |

## 5. 확인용 화면

`ui/VersusCheckScreen`. 로컬 대전 담당이 만드는 꾸미지 않은 화면 하나다.

- **목적**: ① 한 판이 처음부터 승패까지 도는지 눈으로 확인, ② 두 사람이 동시에 키를 누르는 조작감(LB-I01) 확인, ③ 이 문서의 연결 지점을 실제로 쓰는 예제.
- **내용**: 보드 2개, 플레이어별 점수·다음 블럭·레벨, 공격 대기열 격자, 남은 시간, 끝났을 때의 결과 문구. 기존 셀 색만 쓰고 연출은 없다.
- **띄우는 방법**: 시작 메뉴를 건드리지 않도록 실행 인자로 띄운다. `./gradlew run --args="--versus=normal"` (`item`, `time`도 된다). 인자가 없으면 이전과 똑같이 메인 화면이 뜬다. 정식 진입 경로는 UI 담당이 만드는 시작 메뉴 항목이다.
- **키**: 대전 키는 `VersusInput`에 넘긴다. 1인용 설정의 일시정지 키(기본 `P`)로 일시정지·재개, 나가기 키(기본 `Esc`)로 메인 화면. 대전이 끝나거나 멈춘 상태에서 `Enter`는 같은 방식으로 다시 대전, `1`·`2`·`3`은 일반·아이템·시간제한으로 시작한다.
- **창 크기**: 보이는 동안 960×720으로 넓히고, 떠나면 원래 크기로 돌린다. 창이 포커스를 잃으면 `input.releaseAll()`.
- **조립**: `Main`이 `VersusMatch.standard(...)`와 `VersusInput`을 만들고, 설정이 바뀌면 `versusInput.setKeys(settings.versusKeyMap())`를 부른다. 정식 화면도 같은 두 객체를 받으면 된다.
- **주의**: 이 화면은 `KeyboardFocusManager`에 키 디스패처를 등록한다. `onHide`에서 떼지 않으면 다른 화면의 키를 가로챈다(화면 테스트에서 실제로 겪었다).
- UI 담당은 이 화면을 고쳐 쓰거나 새 화면으로 바꾼다. 정식 화면이 들어오면 확인용 화면을 남길지 지울지 그때 정한다.

## 6. UI 담당이 할 일

화면에 대한 요구사항은 `screens.md`에 정리했다.

- [ ] 시작 메뉴에 "대전 모드" 항목 추가 (LB-F01)
- [ ] 게임 방식 선택 화면 → `match.start(mode)` (LB-F02, LB-I06)
- [ ] 2보드 대전 화면: 4.1·4.2의 알림을 받아 그리기 (LB-F06, F08, LB-I02, I08)
- [ ] 대전 화면에서 키 이벤트를 `VersusInput`에 넘기기 (3절)
- [ ] 승자 화면: `onMatchEnded`를 받아 표시, 버튼 세 개 (LB-F14, LB-I07)
- [ ] 일시정지·나가기 (LB-I03)
- [ ] 대전 화면에 들어갈 때 창 폭 넓히기, 나올 때 되돌리기 (LB-I05)
- [ ] 화면에 대전 키 안내 (LB-I04)
- [ ] 설정 화면에 대전 P1·P2 키 영역 (LB-I11, `input.md` 4절)
- [ ] 색맹 모드에서 회색 줄이 구분되는지 확인 (LB-I05)
