# 로컬 대전 구조 설계

근거: `requirements-analysis.md`, `rules.md`. 대상 코드는 2026-11-23 기준 `main`(`f316647`)이다.

> 구현 전 초안이다. 새 클래스·메서드 이름은 계획이며, 구현하면서 바뀌면 이 문서를 고친다.

## 1. 설계 원칙

1. **규칙은 화면과 분리한다.** 공격·대기열·승패는 `game` 쪽에 두고 Swing 화면 없이 테스트한다. P2P가 같은 코드를 쓴다. (LB-N04)
2. **1인 모드는 그대로 둔다.** 엔진에 넣는 연결 지점은 기본값이 "없음"이고, 기존 테스트가 수정 없이 통과해야 한다. (LB-I10)
3. **스레드는 늘리지 않는다.** 엔진 2개 모두 기존 계약대로 Swing EDT에서 돌린다. 두 엔진 사이의 공격 전달에 동기화가 필요 없다.

## 2. 현재 코드의 제약

| 위치 | 현재 | 대전에서 문제가 되는 점 |
| --- | --- | --- |
| `Main.Application` | `GameEngine`·`BlockBoardDriver`·`GameScreen`을 하나씩 만들어 묶음 | 플레이어마다 한 벌이 필요하다. 엔진과 드라이버는 싱글턴이 아니라 여러 개 만드는 데는 문제가 없다 |
| `GameEngine.lockAndAdvance()` | 고정 → 줄 삭제 → 점수 → 아이템 효과 → `spawn()` | 줄 삭제와 생성 사이에 공격을 보내고 받는 지점이 없다 (LB-F07) |
| `GameEngine.LockResult` | 삭제 행 번호와 삭제 전 보드 스냅샷 | 방금 고정한 블럭이 어느 칸이었는지 알 수 없어 공격 줄 모양을 만들 수 없다 (LB-F05) |
| `block.Board` | 칸을 `TetrominoType[][]`으로 저장. 아래에 줄을 끼워 넣는 기능 없음 | 회색 칸을 표현할 값이 없다 (LB-F08) |
| `common.events.CellCodes` | 블럭 ID 0~6만 유효. 8진 자리의 7은 비어 있음 | 화면에 회색 칸을 전달할 코드가 없다 |
| `common.GameMode` | `NORMAL`, `ITEM`. `ScoreboardRepository`가 `valueOf`로 읽고 `ScoreboardScreen`이 `values()`를 돌며 탭을 만듦 | 여기에 대전 방식을 넣으면 스코어보드에 대전 탭이 생긴다 (LB-F15 위반) |
| `ui.GameScreen` | 1,153줄. 보드 그리기·줄 삭제 연출·키 입력이 엔진 하나에 묶여 있음 | 보드 그리기를 두 번 쓸 수 없다. 키 바인딩은 `KeyBindings` 한 벌만 받는다 |
| `ui.PauseScreen` | `GameEngine` 하나와 `ScreenRouter.GAME`으로 돌아가는 동작이 고정 | 대전에서는 두 엔진을 멈추고 대전 화면으로 돌아가야 한다 |
| `ui.ScreenRouter` | 창 크기는 설정의 세로형 3단계(480×640 / 540×720 / 600×800) | 보드 2개가 들어갈 폭이 아니다 |

## 3. 새로 만들 것과 바꿀 곳

```text
common/battle/                  (새 패키지 — block·game·ui·P2P가 함께 쓰는 값)
├── GarbageLine                 공격 줄 한 줄 (10칸 채움 여부)
└── VersusMode                  NORMAL, ITEM, TIME_LIMIT

game/versus/                    (새 패키지 — 화면 없는 대전 규칙)
├── GarbageQueue                공격 대기열 (최대 10줄)
├── AttackLink                  엔진이 공격을 보내고 받는 통로
├── VersusMatch                 엔진 2개·대기열 2개·승패·제한 시간
└── MatchResult                 승자(P1/P2/무승부)와 종료 사유

block/Board                     (변경) 회색 칸 저장, 아래에 줄 삽입
game/BlockBoardDriver           (변경) 공격 줄 계산, 삽입 위임, 스냅샷에 회색 코드
game/GameEngine                 (변경) AttackLink 연결 지점, LockResult에 공격 줄
common/events/CellCodes         (변경) GARBAGE 코드

ui/                             (새 화면·입력 — screens.md, input.md)
├── BattleSelectScreen          게임 방식 선택
├── VersusScreen                2보드 대전 화면
├── VersusResultScreen          승자 화면
└── VersusInput                 창 전체 키 분배
```

### 3.1 `GarbageLine`, `GarbageQueue`

- `GarbageLine`: 한 줄의 10칸 중 어느 칸이 차 있는지를 담는 불변 값. 데이터 표현은 P2P와 공유하므로 `integration.md` 1절에서 정한다.
- `GarbageQueue`: 위에서 아래 순서의 줄 목록.
  - `offer(lines)`: `rules.md` 3절 규칙대로 받고, 실제로 받은 줄 수를 돌려준다. 이미 10줄이면 0.
  - `drain()`: 전부 꺼내고 비운다. 보드 삽입 직전에 부른다.
  - `lines()`: 화면 표시용 읽기 전용 목록.
  - 상한 10은 `GameConstants`에 상수로 둔다.

### 3.2 보드와 드라이버

**회색 칸 저장.** `Board.cellAt()`을 호출하는 곳은 7군데인데, `BlockBoardDriver.snapshot()` 한 곳만 종류를 읽고 나머지(`CollisionChecker`, `LineClearer`, `AnvilPiece`, `BombItem`)는 비었는지만 본다. 그래서 다음 방식을 택한다.

- `Board`가 칸마다 "회색 여부"를 함께 저장하고 `isGarbage(row, col)`로 알려 준다. 회색 칸도 `cellAt()`에서는 차 있는 칸으로 보이므로 충돌·줄 삭제·아이템 코드는 바꾸지 않는다.
- 줄 삭제(`clearLinesIncludingAndGetRows`), 칸 삭제(`eraseCells`), 초기화(`clear`)가 회색 표시를 같이 옮기고 지운다.
- 대안: `TetrominoType`에 여덟 번째 값을 추가하는 방법은 7종 전제(생성기 가중치, `TetrominoTypeTest`)를 깨므로 택하지 않는다.

**줄 삽입.** `Board.insertRowsAtBottom(lines)`: 기존 줄을 위로 밀고 아래에 회색 줄을 넣는다. 맨 위로 밀려난 칸이 하나라도 있었으면 true를 돌려준다(패배 판정용, A6). 부분 적용 상태를 남기지 않도록 기존 줄 삭제와 같이 새 배열을 완성한 뒤 교체한다.

**공격 줄 계산.** `BlockBoardDriver.lockAndClearLines()`가 다음 순서로 만든다.

1. 고정 전에 낙하 조각의 칸(`FallingPiece.cells()`)을 기억한다.
2. 고정하고(아이템 보드 효과 포함) 삭제 전 스냅샷을 찍는다 — 지금도 하는 일이다.
3. 줄을 지우고 삭제 행 번호를 받는다.
4. 삭제 행이 2줄 이상이면, 각 삭제 행에 대해 "스냅샷에서 차 있고 1의 칸이 아닌 칸"을 채운 `GarbageLine`을 위에서 아래 순으로 만든다. 채운 칸이 없는 줄은 뺀다(A4).

결과는 `LockResult`에 `attackLines` 필드로 싣는다. 기존 생성자는 빈 목록을 넣는 편의 생성자로 남긴다.

`BoardDriver` 인터페이스에는 `default boolean insertGarbage(List<GarbageLine>)`(기본 구현은 아무 일도 안 함)를 추가한다.

**표시.** `CellCodes.GARBAGE = 7`을 추가하고 `isValid`가 받아들이게 한다. `BlockBoardDriver.snapshot()`은 회색 칸에 이 코드를 넣는다. 화면은 `UiColors.GARBAGE`·`GARBAGE_GLYPH`(이미 정의됨)와 `> <` 문자로 그린다(`design-spec.md` 셀 표). 색 대신 문자로도 구분되므로 색맹 모드에서도 성립한다(LB-I05).

### 3.3 엔진 연결 지점

`GameEngine`이 대전 상대를 모르게, 좁은 통로 하나만 받는다. 아이템의 `GameEffects`와 같은 방식이다.

```java
/** 엔진이 대전 상대와 공격 줄을 주고받는 통로. 1인 모드는 NONE을 쓴다. */
public interface AttackLink {
    /** 방금 고정한 블럭으로 2줄 이상 지웠을 때, 넘길 줄을 위에서 아래 순으로 전달한다. */
    void send(List<GarbageLine> lines);

    /** 다음 블럭을 만들기 직전에 호출한다. 대기 중인 줄을 모두 꺼내 돌려준다. */
    List<GarbageLine> takeIncoming();

    AttackLink NONE = …;   // 보내는 것은 버리고, 받는 것은 항상 빈 목록
}
```

`lockAndAdvance()`의 순서 (굵은 글씨가 추가분, `rules.md` 4절과 같다).

1. `board.lockAndClearLines()`
2. 줄 삭제 알림, 줄 점수, 누적 줄 수
3. 아이템 효과 (`item.onActivated`)
4. **`attackLines`가 있으면 `link.send(attackLines)`**
5. `topOut`이면 게임 종료
6. **`link.takeIncoming()`이 비어 있지 않으면 `board.insertGarbage(...)`, 넘쳤으면 게임 종료**
7. `spawn()`, 실패하면 게임 종료
8. 타이머 재설정, 보드·미리보기 알림

대기열이 바뀐 것을 화면에 알리는 일은 엔진이 아니라 `VersusMatch`가 한다(대기열의 주인이 `VersusMatch`이므로).

### 3.4 `VersusMode`

`NORMAL`, `ITEM`, `TIME_LIMIT`. `GameMode`와 별개의 타입이라 스코어보드 코드는 대전을 모른다. `ITEM`이면 두 드라이버에 `setItemMode(true)`, `TIME_LIMIT`이면 제한 시간을 건다.

### 3.5 `VersusMatch`

대전 한 판의 진행을 맡는다. 화면은 이 클래스만 상대한다.

- **구성**: 플레이어마다 `BlockBoardDriver` + `GameEngine` + `GarbageQueue`. P1의 `AttackLink.send`는 P2의 대기열에 `offer`하고, P1의 `takeIncoming`은 P1의 대기열을 `drain`한다. P2는 반대다.
  - 아이템 등장 규칙(`ItemPolicy`)은 드라이버가 각자 가지므로 플레이어별 누적 10줄이 따로 세어진다. `ItemRegistry`는 함께 쓴다.
- **조작**: `start(mode)`, `handle(player, action)`, `pause()`, `resume()`, `stop()`. 일시정지·재개·중단은 두 엔진에 함께 건다.
- **승패**: 각 엔진의 `onGameOver`를 받아, 먼저 끝난 쪽을 패자로 정하고 상대 엔진을 `stop()`한 뒤 `MatchResult`를 한 번만 알린다.
- **제한 시간**: `TIME_LIMIT`에서 1초 간격으로 남은 시간을 줄인다. 엔진의 `DropTimer`를 주입받아 테스트에서 시간을 직접 넘긴다. 일시정지 중에는 멈춘다. 0이 되면 두 엔진을 멈추고 점수를 비교한다.
- **알림**: 플레이어별 `GameEventListener`(보드·점수·다음 블럭 등 기존 이벤트 그대로)와 대전 전용 알림(대기열 변경, 남은 시간, 결과).

플레이어별 표시 정보가 기존 `GameEventListener` 그대로 흐르므로, 화면의 플레이어 패널은 상대가 로컬 엔진인지 네트워크인지 구분하지 않는다. P2P 통합의 접점이다(`integration.md` 2절).

### 3.6 `Main`

`Application`에 대전용 조립을 추가한다. 1인용 엔진·화면은 그대로 두고, `VersusMatch`와 대전 화면 셋을 만들어 `ScreenRouter`에 등록한다. 설정 변경(`applySettings`)이 대전 화면의 색 구성에도 전달되게 한다.

## 4. 성능 (LB-N01)

- 엔진 하나가 쓰는 Swing 타이머는 낙하용과 1초 점수용 둘이다. 대전은 이것이 두 벌이고 제한 시간용 하나가 더해진다. 모두 이벤트가 있을 때만 깨어난다.
- 화면은 이벤트가 왔을 때 해당 플레이어 영역만 다시 그린다. 상시 다시 그리기 루프는 두지 않는다(`design-spec.md` §7.3).

## 5. 확인이 필요한 설계 판단

| 판단 | 이유 | 확인 대상 |
| --- | --- | --- |
| `block.Board`에 회색 표시와 삽입 기능 추가 | `block` 패키지는 game·ui를 import하지 않는다는 규칙을 지키면서 회색 줄을 저장하려면 보드가 알아야 한다. `GarbageLine`을 `common/battle`에 두는 것도 이 규칙 때문이다 | `block` 담당 리뷰 |
| `GameScreen`의 보드 그리기를 공용 부품으로 분리 | 대전 화면이 같은 셀 그리기를 써야 한다. UI 리뉴얼과 같은 파일을 건드린다 | `integration.md` 3절 |
| `PauseScreen`을 엔진 대신 작은 인터페이스에 의존하게 변경 | 대전에서 재사용하려면 "재개·나가기·돌아갈 화면"만 알면 된다 | UI 담당 |
