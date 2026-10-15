# 공용 인터페이스 정리

기준: 2026-10-15, `main`(68eba82)과 열려 있는 PR #34~#38, #40~#42.

여러 담당자가 함께 쓰는 인터페이스와, 열린 PR들이 그것을 어떻게 넓히는지 한곳에 모았다.
연결 방식 전체는 `person2-integration.md`, 블록 계약은 `block/block_integration_contract.md`,
아이템 구조는 `items/item-structure.md`를 본다.

## 확장 원칙

여러 PR이 같은 인터페이스를 동시에 넓히고 있다. 열린 PR들이 이미 따르고 있는 아래 방식을 지키면
서로의 구현을 고치지 않고 함께 머지할 수 있다.

1. 기존 인터페이스에 메서드를 추가할 때는 `default` 메서드로 추가한다. 기존 구현체와 테스트용
   가짜 구현을 고치지 않아도 컴파일된다.
2. 정책에 새 입력이 필요하면 기존 메서드를 바꾸지 않는다. 인자를 더 받는 `default` 오버로드를 추가하고
   기본 구현에서 재정의한다.
3. 화면으로 보내는 ID는 고정 숫자 규칙을 쓰고 enum의 `ordinal()`을 쓰지 않는다. 범위가 겹치지 않게 한다.
4. `game`은 `ui`를 import하지 않고, `block`은 `game`·`ui`를 import하지 않는다.
   엔진 호출과 리스너 콜백은 Swing EDT에서 한다.

## 게임 → 화면: `common.events.GameEventListener`

`GameEngine`이 호출하고 `GameScreen`이 구현한다. `Main.ForwardingListener`가 둘 사이를 전달한다.

| 메서드 | 추가한 곳 | 내용 |
| --- | --- | --- |
| `onBoardUpdated(BoardSnapshot)` | main | 낙하 중 블록을 포함한 20×10 보드 |
| `onScoreChanged(int)` | main | 점수 |
| `onNextBlocksChanged(int[])` | main | 미리보기 ID 배열 |
| `onPauseStateChanged(boolean)` | main | 일시정지 여부 |
| `onGameOver(int)` | main | 최종 점수 |
| `onLandingUpdated(BoardSnapshot, BoardSnapshot)` | #35, default | 낙하 중 블록만 담은 보드, 착지 위치(고스트) |
| `onLinesCleared(List<Integer>)`, `onLinesCleared(List<Integer>, BoardSnapshot)` | #35, default | 지운 행 번호, 지우기 직전 보드 (줄 삭제 애니메이션) |
| `onFeverChanged(int)` | #40, default | 피버 남은 초. 0이면 피버 종료 |

`Main.ForwardingListener`는 메서드를 하나씩 명시적으로 전달한다. 콜백을 새로 추가하면 여기에도 전달을
추가해야 화면까지 도착한다. #35는 함께 추가했고, #40의 `onFeverChanged` 전달은 UI 담당 요청 사항이다.

## 화면 ID 규칙: `BoardSnapshot`과 미리보기 배열

| 값 | 의미 | 정의 | 추가한 곳 |
| --- | --- | --- | --- |
| `-1` | 빈칸 | `BoardSnapshot.EMPTY` | main |
| `0..6` | 일반 블록 I, O, T, S, Z, J, L | `TetrominoType.id()` | main |
| `35` | 무게추 (보드·미리보기 공용) | `AnvilIds.ANVIL_ID` | #36 |
| `100 + 아이템 ID × 10 + 블록 ID` | 아이템이 붙은 칸 (보드) | `ItemCellIds.boardCell` | #40~#42 공통 커밋 |
| `1000 + 아이템 ID × 100 + 블록 ID × 4 + 칸 번호` | 아이템이 붙은 블록 (미리보기) | `ItemCellIds.preview` | #40~#42 공통 커밋 |

- 아이템 ID는 `ItemType.id()`이다: `NONE` 0, `LINE_DELETE`(L) 1, `ANVIL`(W) 2, `FEVER`(F) 3, `BOMB`(B) 4, `SLOW_DOWN`(S) 5.
- #34의 L은 처음에 보드 `7..13`, 미리보기 `7..34`를 썼다. 아이템이 늘면 범위가 겹치므로 공통 커밋에서
  위 규칙으로 옮겼다. `LineDeleteIds`는 같은 메서드 이름을 유지하므로 #34·#36의 `GameScreen` 코드는 그대로 동작한다.
- #35의 고스트용 `fallingSnapshot()`·`landingSnapshot()`은 일반 블록 ID(`0..6`)만 쓴다.

## 엔진 ↔ 보드: `game.GameEngine.BoardDriver`

`GameEngine`이 호출하고 `BlockBoardDriver`가 구현한다.

| 메서드 | 추가한 곳 | 내용 |
| --- | --- | --- |
| `reset`, `spawnNextBlock`, `moveLeft`, `moveRight`, `moveDown`, `rotateClockwise`, `hardDrop`, `lockAndClearLines`, `snapshot`, `nextBlockTypes` | main | `person2-integration.md`의 "담당자 1: 블록 연결 계약" |
| `fallingSnapshot()`, `landingSnapshot()` | #35, default `null` | 고스트 표시용 |
| `awardsDropPoints()` | #36, default `true` | 무게추 낙하에는 점수를 주지 않음 |
| `configureDifficulty(Difficulty)` | #38, default 빈 동작 | 난이도별 블록 생성 가중치 |
| `lockedItem()` | #40~#42 공통 커밋, default `NONE` | 방금 고정한 블록에 붙어 있던 아이템. 엔진은 이 값으로 피버·속도 감소처럼 엔진 상태를 바꾸는 효과를 켬 |

`GameEngine.LockResult`:

- main: `(clearedLines, topOut)`
- #35: `clearedRows`, `boardBeforeClear`를 추가했다. 기존 2인자 생성자는 남아 있다.
- 아이템 PR은 `LockResult`를 바꾸지 않고 `lockedItem()`으로 따로 알린다. #35의 변경과 겹치지 않게 하기 위해서다.

## 정책과 타이머

### `game.SpeedPolicy`

- main: `calculate(생성 블록 수, 삭제 줄 수)`
- #38: `calculate(생성 블록 수, 삭제 줄 수, Difficulty)` default 오버로드
- #42: `calculate(생성 블록 수, 삭제 줄 수, 낮춘 단계 수)` default 오버로드. `DefaultSpeedPolicy`가 구현한다.

### `game.ScoringPolicy`

- main: `dropPoints`, `lineClearPoints`, `add`
- #38: `lineClearPoints(줄 수, 레벨, Difficulty)` default 오버로드
- 피버(#40)는 정책을 바꾸지 않고 엔진의 `award()`에서 점수를 2배로 만든다. 따라서 #38과 함께 쓰면
  난이도가 적용된 점수가 2배가 되고, #38의 생존 점수(1초당 1점)도 `award()`를 거치므로 피버 중에는 2배가 된다.

### `game.GameEngine.DropTimer`

- main: 자동 낙하 타이머
- #38: 생존 점수용 `survivalTimer` (7인자 생성자, `Supplier<Difficulty>`와 함께)
- #40: 피버용 `feverTimer` (6인자 생성자)
- 둘 다 테스트에서 가짜 타이머를 넣을 수 있도록 생성자로 받는다.

## 블록: `block.Board`

| 메서드 | 추가한 곳 | 내용 |
| --- | --- | --- |
| `clearFullLines()` | main | 완성 행 삭제, 삭제 수 반환 |
| `clearLinesIncluding(int forcedRow)` | #34 | 완성 행과 L 아이템 행을 함께 삭제 |
| `clearFullLinesAndGetRows()` | #35 | 완성 행 삭제, 삭제 전 행 번호 반환 (줄 삭제 애니메이션) |
| `eraseCells(List<Cell>)` | #36 | 행을 압축하지 않고 지정한 칸만 지움. #41 폭탄도 사용 |

## 아이템: `game.ItemType`

- #36이 `NONE`, `LINE_DELETE`, `ANVIL`로 만들었다. #34는 enum 없이 L이 붙은 칸 번호만 다뤘다.
- #40~#42 공통 커밋에서 고정 ID·문자·포함형 여부·`earnable()`·`fromId()`를 추가했다.
  #40~#42가 각각 `FEVER`, `BOMB`, `SLOW_DOWN`을 추가한다.
- 10줄마다 나오는 아이템은 #36의 L/무게추 50% 선택에서 `earnable()` 전체 균등 선택으로 바뀌었다.
- 자세한 내용과 새 아이템 추가 방법은 `items/item-structure.md`를 본다.

## 설정과 난이도

- #38: `common.Difficulty`(EASY/NORMAL/HARD, 블록 가중치·속도·점수 값), `GameSettings.difficulty()`, `ScoreRecord.difficulty()`
- #37: `GameSettings.Difficulty`와 `scoreboard.ScoreDifficulty`를 따로 정의한다.
- 같은 개념의 enum이 세 개가 되므로 함께 머지할 때 하나로 맞춰야 한다.
  `GameSettings` 레코드의 필드 순서도 두 PR이 다르다. #37은 `difficulty, keyBindings`, #38은 `keyBindings, difficulty`이다.

## 화면 진입

- #34: `MainMenuScreen(router, startNormal, startItemMode)`. `Main.startGame(boolean itemMode)`가
  `BlockBoardDriver.setItemMode`를 호출하고 게임을 시작한다.
- 현재 #34·#36에서는 생성자로 받은 두 동작이 메뉴 항목에 연결되어 있지 않아 아이템 모드로 들어갈 수 없다.

## 함께 머지할 때 정리할 지점

| PR | 겹치는 곳 | 정리 방법 |
| --- | --- | --- |
| #34 + #35 | `Board` 줄 삭제, `BlockBoardDriver.lockAndClearLines` | 강제 행을 포함한 삭제가 행 번호를 돌려주도록 합친다. 로컬에서 `clearLinesIncludingAndGetRows(int)`로 합쳐 테스트 통과를 확인했다 |
| #38 + #40 | `GameEngine` 생성자 | 생존 타이머와 피버 타이머를 모두 받는 생성자 하나로 합친다 |
| #38 + #42 | `SpeedPolicy`, `GameEngine.updateSpeed()` | 난이도와 낮춘 단계 수를 모두 받는 메서드 하나로 합친다 (`items/slow-down.md`) |
| #37 + #38 | 난이도 enum, `GameSettings`, `ScoreRecord` | enum 하나로 맞춘다 |
| #40, #41, #42 | `ItemType`, `items/item-structure.md`, #40·#42는 `GameEngine.activateItem`도 | 각자 추가한 줄을 모두 남긴다 |
| UI 반영 | 아이템 칸 그리기, `onFeverChanged` 전달 | `items/item-structure.md`의 "화면 연동" |
