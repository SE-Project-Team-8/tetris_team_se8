# 줄 삭제 아이템(`L`) 명세

근거: `docs/requirments2.md` 4.3. 공통 구조는 `README.md`를 본다.

## 표시와 등장

- **포함형** 아이템이다. 아이템 기회로 선택되면 일반 블록의 네 칸 중 무작위 한 칸에 문자 `L`이 붙는다
  (`BlockBoardDriver`가 큐에 넣을 때 칸을 정한다). 이동·회전 중에도 `L`은 같은 칸을 따라간다.
- 예: I형은 `OOOL`, `OOLO`, `OLOO`, `LOOO` 중 하나가 된다.

## 효과

1. 블록이 더 이상 움직이지 못해 고정되면 `L`이 있는 행을 **꽉 차 있지 않아도** 지운다.
2. 그 행은 완성 행과 같은 삭제·애니메이션·점수 경로를 타므로 기존 줄 점수 규칙을 그대로 따른다.
3. `L` 행이 이미 완성 행이면 한 번만 센다.

## 연결 구조

- `game/item/LineDeleteItem`: `onLock(board, itemCell)`이 `Set.of(itemCell.row())`를 돌려준다. 보드는 바꾸지 않는다.
- `game/TetrominoPiece.lock()`이 그 결과를 `LockOutcome.clearLines(forcedRows)`로 넘기고,
  `BlockBoardDriver`가 `Board.clearLinesIncludingAndGetRows(forcedRows)`로 완성 행과 함께 지운다.

## 검증

- `LineDeleteItemTest`: 아이템 칸의 행만 돌려주고 보드를 바꾸지 않는다.
- `BlockBoardDriverTest`: 10줄 후 등장, 부분 행 삭제, 기존 줄 점수 규칙 적용.
- `ItemContractTest`: 문자 고유성과 실제 드라이버 경로.
