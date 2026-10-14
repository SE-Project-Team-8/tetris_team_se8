# 아이템 공통 구조

근거: `docs/requirments2.md` 4.2 (아이템마다 다른 문자), 4.5 (아이템을 쉽게 추가할 수 있는 구조).
기능 브랜치: `refactor/item-common`. 개별 아이템 명세는 같은 폴더의 `anvil.md`, `fever.md` 등을 본다.

## 아이템 종류: `game.ItemType`

| 값 | ID | 문자 | 형태 |
| --- | --- | --- | --- |
| `NONE` | 0 | – | 일반 블록 |
| `LINE_DELETE` | 1 | `L` | 포함형 (블록 한 칸에 붙음) |
| `ANVIL` | 2 | `W` | 블록형 (블록 자체가 아이템) |

- ID는 스냅샷·미리보기로 전달하는 고정 코드이며 enum 순서(ordinal)를 쓰지 않는다.
- 아이템마다 문자가 달라야 한다. `ItemTypeTest`가 ID·문자 중복을 막는다.
- `ItemType.earnable()`: 10줄 아이템 기회로 얻을 수 있는 아이템(`NONE` 제외).

## 등장과 선택: `game.BlockBoardDriver`

- 아이템 모드에서 누적 10줄마다 아이템 기회를 하나 얻고, 다음에 미리보기에 들어가는 블록이 아이템이 된다.
- 기본 선택기는 `ItemType.earnable()` 중 하나를 **균등한 확률**로 고른다. 값을 추가하면 자동으로 후보가 된다.
- 포함형 아이템은 블록의 4칸 중 무작위 한 칸에 붙고, 이동·회전 중에도 같은 칸을 따라간다.
- 블록이 고정되면 `GameEngine.BoardDriver.lockedItem()`이 그 블록에 붙어 있던 아이템을 알려 준다.
  엔진은 이 값으로 점수·속도처럼 엔진이 가진 상태를 바꾸는 효과를 켠다. 기본값은 `NONE`이다.

## 화면 전달 ID: `common.events`

| 대상 | 규칙 |
| --- | --- |
| 일반 블록 | `0..6` (`TetrominoType.id()`) |
| 무게추 | `AnvilIds.ANVIL_ID = 35` (보드·미리보기 공용) |
| 포함형 아이템 칸 (보드) | `ItemCellIds.boardCell(아이템 ID, 블록 ID)` = `100 + 아이템 ID × 10 + 블록 ID` |
| 포함형 아이템 블록 (미리보기) | `ItemCellIds.preview(아이템 ID, 블록 ID, 칸 번호)` = `1000 + 아이템 ID × 100 + 블록 ID × 4 + 칸 번호` |

- 해석: `ItemCellIds.isBoardCell/boardItem/boardType`, `isPreview/previewItem/previewType/previewCellIndex`.
- `LineDeleteIds`는 위 규칙에서 L 부분만 꺼내 쓰는 축약형이다. L만 해석하는 기존 화면 코드가 그대로 동작하도록
  남겨 두었다. (L의 ID 숫자는 보드 `7..13` → `100`번대, 미리보기 `7..34` → `1000`번대로 바뀌었다.)

## 새 아이템 추가 방법

1. `ItemType`에 고유한 ID·문자·형태로 값을 추가한다.
2. 효과를 구현한다.
   - 보드를 바꾸는 효과(줄 삭제, 칸 파괴 등): `BlockBoardDriver.lockAndClearLines()`에서 처리한다.
   - 엔진 상태를 바꾸는 효과(점수, 속도 등): `GameEngine`에서 `board.lockedItem()`을 보고 처리한다.
3. `docs/items/`에 명세를 쓰고 테스트를 추가한다.

## 화면 연동 (UI 담당 요청)

현재 `GameScreen`은 L(`LineDeleteIds`)과 무게추(`AnvilIds`)만 해석한다. 새 포함형 아이템이 화면에 보이려면
보드·미리보기 그리기에서 `ItemCellIds`인 칸을 블록 색으로 그리고
`ItemType.fromId(아이템 ID).letter()` 문자를 얹어야 한다. 이렇게 바꾸면 L도 같은 경로로 그려지므로
`LineDeleteIds`는 지울 수 있다.
