# 아이템 구조와 추가 방법

근거: `docs/requirments2.md` 4.2(두 가지 형태, 아이템마다 다른 문자), 4.5(팀 자체 아이템 명세), 4.6(아이템을 쉽게 추가할 수 있는 구조).
개별 아이템 명세는 같은 폴더의 `line-delete.md`, `anvil.md`, `bomb.md`, `fever.md`, `slow-down.md`를 본다.

## 구조

| 역할 | 위치 | 설명 |
| --- | --- | --- |
| 아이템 계약 | `game/item/Item` (sealed) | `name()`, `symbol()`. 하위 타입은 아래 둘뿐이라 형태를 가르는 switch는 컴파일러가 빠짐없이 검사한다 |
| 포함형 | `EmbeddedItem` | 기본 블록의 한 칸에 붙는다. `onLock(Board, Cell)`에서 보드 효과를 적용하고 강제로 지울 행을 돌려준다 |
| 블록형 | `StandaloneItem` | 블록 자체가 아이템이다. `paletteType()`(색), `previewShape()`(미리보기 모양), `spawn(Board)` → `FallingPiece` |
| 낙하 조각 | `FallingPiece` | 이동·회전·낙하·고정 규칙. 일반 블록은 `game/TetrominoPiece`, 무게추는 `AnvilPiece` |
| 고정 결과 | `LockOutcome` | 줄 삭제를 진행할지(`clearLines`)와 강제 삭제 행, 또는 소모만 할지(`consumed`) |
| 엔진 효과 | `GameEffects` | `Item.onActivated`에서 받는 좁은 통로. `scoreMultiplier(배율, 초)`, `lowerLevel(단계)`. `GameEngine`이 내부 클래스로 구현한다 |
| 등록 | `ItemRegistry.defaults()` | **유일한 등록 지점.** 이름·문자 중복과 보이지 않는 문자를 거부한다 |
| 등장 규칙 | `ItemPolicy` | `none()`은 일반 모드, `everyLines(10, selector)`는 아이템 모드(누적 10줄마다 하나) |
| 선택 | `ItemSelector`, `WeightedItemSelector` | 룰렛 휠. 실제 게임은 `uniform(registry, random)`으로 균등 선택 |
| 화면 코드 | `common/events/CellCodes` | `baseType + 8 × symbol` (+ 미리보기는 칸 번호). 화면은 문자와 색만 디코드하고 아이템 종류를 모른다 |

흐름: 줄 삭제 → `ItemPolicy.onLinesCleared` → 큐를 채울 때 `nextItem()` → `QueuedPiece`(포함형이면 무작위 칸에 부착)
→ `spawn` → `FallingPiece` → `lock` → `LockOutcome` → 드라이버가 줄 삭제·점수 경로로 넘김.

`BlockBoardDriver`는 보드와 큐만 소유하고, `GameEngine`과 `GameScreen`은 아이템 종류를 전혀 모른다.

## 새 아이템 추가 체크리스트

1. `game/item/`에 클래스 하나를 만든다. 포함형이면 `EmbeddedItem`, 블록형이면 `StandaloneItem`을 구현한다.
2. `ItemRegistry.defaults()` 목록에 한 줄 추가한다.
3. `docs/items/<이름>.md`에 명세(형태, 문자, 효과, 점수 규칙)를 쓴다. 평가 팀이 구현을 확인하는 근거다(4.5).
4. 아이템 고유 동작의 테스트를 쓴다. 문자 고유성, 미리보기, 생성, 10줄 후 실제 드라이버 경로는
   `ItemContractTest`가 등록된 모든 아이템에 대해 자동으로 검사한다.

이 네 단계 밖의 파일(`BlockBoardDriver`, `GameEngine`, `GameScreen`, `Main`, `CellCodes`)은 건드리지 않는다.

### 예: 3×3 폭탄 (포함형, 보드 효과) — 실제 구현은 `BombItem`

```java
public final class BombItem implements EmbeddedItem {
    @Override public String name() { return "BOMB"; }
    @Override public char symbol() { return 'B'; }

    @Override
    public Set<Integer> onLock(Board board, Cell center) {
        board.eraseCells(cellsAround(board, center)); // 보드 효과는 여기서 직접 적용
        return Set.of();                               // 강제로 지울 행은 없음
    }
}
```

`ItemRegistry.defaults()`에 `new BombItem()`을 추가하면 등장·미리보기·문자 표시·색맹 팔레트 호환이 모두 따라온다.

### 예: 블록형

`StandaloneItem`을 구현하고 `spawn(Board)`에서 자기 `FallingPiece`를 돌려준다. 조작 규칙(회전 가능 여부,
낙하 점수 여부, 바닥에서 소모할지 고정할지)은 그 조각이 정한다. `AnvilItem`/`AnvilPiece`를 그대로 따라 쓰면 된다.
`previewShape()`의 너비는 보드(10칸) 안에 들어가야 한다.

## 효과 종류별 붙는 자리

| 효과 | 붙는 자리 | 예 |
| --- | --- | --- |
| 행 삭제 | `EmbeddedItem.onLock`의 반환값 | 줄 삭제 `L` |
| 칸 파괴, 보드 변경 | `onLock` 안에서 `Board.eraseCells` 등 호출 | 폭탄 `B` (`BombItem`) |
| 낙하 중 보드 변경, 조작 제한, 점수 면제 | `FallingPiece` 구현 | 무게추 `W` |
| 엔진 상태 변경 (점수 배율, 속도 단계) | `Item.onActivated(GameEffects)`: `scoreMultiplier`, `lowerLevel`. 조각이 고정되고 그 조각 자신의 낙하·줄 점수가 반영된 뒤 한 번 호출된다 | 피버 `F` (`FeverItem`), 속도 감소 `S` (`feat/item-3-slow-down`) |

## 기존 아이템 브랜치를 옮길 때

- `ItemType` enum, `ItemCellIds`, `LineDeleteIds`, `AnvilIds`는 없어졌다. 코드는 `CellCodes.cell(typeId, symbol)` /
  `CellCodes.preview(typeId, symbol, cellIndex)`이며, 아이템 ID 숫자를 배정할 필요가 없다.
- 보드 효과는 아이템의 `onLock`에서 직접 적용한다.
- `BoardDriver.lockedItem()`이 돌아왔다. 반환형은 `Item`(일반 블록은 null)이며, 엔진은 이것으로 `onActivated`를 부른다.
  아이템 쪽에서 `lockedItem()`을 직접 볼 일은 없고, 엔진 효과는 `onActivated`에서 `GameEffects`로 요청한다.
  화면 표시는 `GameEngine.getScoreMultiplierSeconds()`, `getSlowdownSteps()` 같은 getter로 하며 `GameEventListener`는 그대로다.
- `block/Bomb.blastCells` 같은 범위 계산은 아이템 클래스 안이나 `game/item/`에 둔다. `block`은 보드 연산만 남긴다.
- `BlockBoardDriver(board, generator, random, ItemSelector)` 생성자로 선택기를 고정하면 기존 아이템 테스트 흐름을 그대로 쓸 수 있다.

## 제약

- `GameEngine.LockResult`와 `ScoreCalculator`는 한 번에 최대 4줄을 가정한다. 5줄 이상을 강제 삭제하는 아이템을
  만들려면 상한과 점수표를 먼저 정한다.
- `onLock`과 `FallingPiece`의 메서드는 Swing EDT에서 호출된다. 시간이 걸리는 작업을 두지 않는다.
