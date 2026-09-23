# block 연동 계약 — 다른 담당자용

- 기준일: 2026-09-23 / 대상: 담당자 2, B, D
- 제공 패키지: `com.team.tetris.block` / 구현 담당: 담당자 1
- 근거: [1차 요구사항 정리](requirments1.md), [역할 분담](roles.md)
- 내부 설계·구현·테스트 이력: [block 개발 문서](block_implementation_plan.md)

이 문서는 다른 담당자가 내부 구현을 읽지 않고도 `block`을 연결할 수 있도록 공개 API, 데이터 규약, 실패 처리, 호출 순서와 인계 사항을 모은다. **현재 코드가 제공하는 계약**과 **상위 모듈의 연결 예제·확인 사항**을 구분한다. 문서 작성이 팀 전체의 연동 완료나 합의를 뜻하지는 않는다.

## 1. 담당 경계와 연결 원칙

| 담당 | 제공하거나 구현할 내용 |
| --- | --- |
| 담당자 1 | 종류·좌표·회전, 고정 보드, 충돌, 드롭 거리, 행 삭제, 균등 생성기, 색상 매핑 |
| 담당자 2 | 게임 상태·입력 해석·타이머·현재 블록·생성 위치·다음 큐·점수·게임 종료·이벤트, 설정·스코어보드와 담당 화면 |
| B | 보드·미리보기 렌더링, 종류 ID→색상 변환, 문자·무늬와 색각 식별성 등 UI |
| D | 빌드·CI·배포, 전체 커버리지 기준, NFR·Windows·최소 사양 검증 |

- 엔진은 세션의 `Board`, 현재 `Tetromino`, 다음 블록 큐와 생성기를 관리한다. `Board`는 고정된 셀만 저장한다.
- 계산에는 `BoardView`, 실제 고정·삭제·초기화에는 `Board`를 사용한다. UI에 변경 가능한 보드를 전달하지 않는다.
- `block`은 `game`·`ui`·`settings`·`scoreboard`를 호출하지 않는다. 점수·이벤트·설정 저장도 처리하지 않는다.
- 같은 세션의 조회·변경은 엔진이 직렬화한다. `BoardView`는 읽기 전용 접근일 뿐 복사본이나 스레드 안전 보장이 아니다.
- `LineClearer`와 로컬 형태 조회는 패키지 내부용이다. 외부 모듈에서 직접 사용하지 않는다.

## 2. 데이터와 동작 규약

### 2.1 좌표·보드·블록

- 좌표는 `(row, col)`이다. 좌상단은 `(0, 0)`, 아래로 row 증가, 오른쪽으로 col 증가한다.
- 기본 보드는 `GameConstants.BOARD_ROWS = 20`, `BOARD_COLS = 10`이다. 화면 배율 변경은 논리 보드 크기를 바꾸지 않는다.
- `Tetromino.row()`·`col()`은 회전 격자의 기준점이다. 실제 점유 위치는 `cells()`의 네 셀로 판단한다.
- 기준점은 음수일 수 있지만 실제 점유 셀은 모두 보드 안이어야 배치 가능하다. 숨겨진 상단 행은 제공하지 않는다.
- `Cell`, `Tetromino`는 불변 값이다. 이동·회전은 새 후보를 반환하며 보드 검사나 현재 블록 교체를 하지 않는다.
- 회전은 시계 방향 90도, `SPAWN → RIGHT → REVERSE → LEFT → SPAWN`이다. 벽·고정 셀 충돌 시 후보를 거절하며 wall kick은 없다.
- I는 4×4, O는 2×2, 나머지는 3×3 로컬 격자이다. O는 회전 상태만 바뀌고 점유 셀은 그대로다.

### 2.2 ID·초기 형태·팔레트

다음 값은 현재 구현값이다. ID는 `id()`·`fromId()`로 변환하며 `ordinal()`에 의존하지 않는다. 초기 형태의 `/`는 행 구분, `1`은 점유 셀, `0`은 빈 로컬 셀이다.

| 종류 | ID | SPAWN 로컬 형태 | 일반 RGB | 색맹 보조 RGB |
| --- | --- | --- | --- | --- |
| I | 0 | `0000/1111/0000/0000` | `0x00FFFF` | `0x56B4E9` |
| O | 1 | `11/11` | `0xFFFF00` | `0xF0E442` |
| T | 2 | `010/111/000` | `0xFF00FF` | `0xCC79A7` |
| S | 3 | `011/110/000` | `0x00FF00` | `0x009E73` |
| Z | 4 | `110/011/000` | `0xFF0000` | `0xD55E00` |
| J | 5 | `111/001/000` | `0x0000FF` | `0x0072B2` |
| L | 6 | `111/100/000` | `0xFFA500` | `0xE69F00` |

빈 칸은 스냅샷에서 `BoardSnapshot.EMPTY = -1`, 두 팔레트의 배경색은 `0x10141C`이다. 보드 조회에서는 빈 칸을 `Optional.empty()`로 표현한다. 빈 칸은 테트로미노 종류가 아니므로 `fromId(-1)`을 호출하면 예외가 발생한다.

RGB는 알파 없는 `0x000000~0xFFFFFF`의 정수다. 색상 모드를 바꿔도 보드 종류·ID는 바뀌지 않는다. 색맹 보조 팔레트는 [Okabe·Ito의 Color Universal Design](https://jfly.uni-koeln.de/color/)을 참고했으며 실제 UI 접근성 통과를 보증하지 않는다.

### 2.3 생성·고정·삭제·드롭

- 생성기는 주입된 `RandomGenerator.nextInt(7)`을 호출해 종류 하나를 반환한다. 균등 난수원 기준 매번 각 종류의 확률은 1/7이며 연속 중복도 허용한다. 7-bag·재추첨은 없다.
- 생성 위치와 미리보기 큐는 엔진 책임이다. 공용 `NEXT_QUEUE_SIZE`는 현재 1이다.
- `tryPlace`는 네 셀이 모두 배치 가능할 때만 고정한다. 실패하면 보드는 그대로다. 착지 여부는 검사하지 않으므로 엔진이 고정 시점을 결정해야 한다.
- 고정과 행 삭제는 별도 호출이다. `clearFullLines()`는 완성 행 전체를 제거하고 생존 행 순서를 유지해 아래로 내린다. 빈 행은 위에 생긴다.
- 삭제 수는 일반 플레이에서 0~4지만 API는 이미 존재하는 모든 완성 행을 처리하므로 4 초과도 반환할 수 있다.
- 드롭 거리는 현재 유효 위치에서 장애물을 통과하지 않고 연속으로 내려갈 수 있는 최대 행 수다. 계산만 하며 이동·고정·점수 갱신을 하지 않는다.
- `clear()`는 고정 보드만 초기화한다. 현재 블록·큐·점수·타이머·설정은 초기화하지 않는다.

## 3. 공개 API

아래는 생성자와 도메인 공개 메서드다. record의 동등성·접근자, enum의 표준 메서드는 Java 기본 규약을 따른다. 컬렉션 타입은 `java.util`, 난수 계약은 `java.util.random.RandomGenerator`이다.

### 3.1 값과 형태

| 타입 | 생성자·메서드 | 의미 |
| --- | --- | --- |
| `Cell` | `Cell(int row, int col)`, `int row()`, `int col()` | 불변 좌표. 자체 보드 범위 검사는 없음 |
| `Rotation` | `SPAWN`, `RIGHT`, `REVERSE`, `LEFT`; `Rotation clockwise()` | 다음 시계 방향 상태 |
| `TetrominoType` | `int id()`; `static TetrominoType fromId(int id)` | 명시적 종류 ID 변환 |
| `Tetromino` | `Tetromino(TetrominoType type, Rotation rotation, int row, int col)` | 종류·회전·기준점으로 생성 |
| `Tetromino` | `type()`, `rotation()`, `row()`, `col()` | record 필드 접근자 |
| `Tetromino` | `List<Cell> cells()` | 실제 보드 좌표 네 개의 수정 불가 목록 |
| `Tetromino` | `Tetromino moveBy(int deltaRow, int deltaCol)` | 이동 후보 반환 |
| `Tetromino` | `Tetromino rotateClockwise()` | 같은 기준점의 회전 후보 반환 |

### 3.2 보드와 계산

| 타입 | 생성자·메서드 | 의미 |
| --- | --- | --- |
| `BoardView` | `int rows()`, `int cols()` | 조회 대상의 행·열 수 |
| `BoardView` | `Optional<TetrominoType> cellAt(int row, int col)` | 고정 셀 조회 |
| `Board implements BoardView` | `Board()`, `Board(int rows, int cols)` | 기본 또는 지정 크기의 빈 보드 |
| `Board` | `boolean tryPlace(Tetromino piece)` | 전체 검사 후 네 셀 고정 |
| `Board` | `int clearFullLines()` | 완성 행 제거 후 삭제 수 반환 |
| `Board` | `void clear()` | 크기를 유지하며 고정 셀 비우기 |
| `CollisionChecker` | `static boolean canPlace(BoardView board, Tetromino piece)` | 경계·고정 셀 충돌 검사 |
| `DropCalculator` | `static int dropDistance(BoardView board, Tetromino piece)` | 유효한 시작점에서 최대 낙하 거리 계산 |

`BoardView`를 별도 구현할 때도 양의 크기, 범위 안 빈 셀의 `Optional.empty()`, 범위 밖 조회의 `IndexOutOfBoundsException` 계약을 지킨다. 계산 중 크기·셀 상태가 바뀌는 뷰는 지원하지 않는다.

### 3.3 생성과 색상

| 타입 | 생성자·메서드 | 의미 |
| --- | --- | --- |
| `TetrominoGenerator` | `TetrominoType next()` | 다음 종류 하나. 함수형 인터페이스 |
| `UniformTetrominoGenerator` | `UniformTetrominoGenerator(RandomGenerator random)` | 주입 난수원으로 균등 생성 |
| `UniformTetrominoGenerator` | `TetrominoType next()` | 난수 표본 하나를 종류 하나로 매핑 |
| `ColorScheme` | `ColorScheme(Map<TetrominoType, Integer> colors, int emptyRgb)` | 완전성·RGB·중복 검증 후 입력 맵 복사 |
| `ColorScheme` | `static ColorScheme standard()`, `static ColorScheme colorBlind()` | 공유 불변 팔레트 |
| `ColorScheme` | `int rgbOf(TetrominoType type)`, `int emptyRgb()` | 종류·빈 칸 색상 조회 |

## 4. 반환값과 예외

벽·바닥·블록에 막히는 것은 게임 중 정상 상황이다. 예외를 던져 입력을 거절하거나 충돌마다 catch하지 않는다.

| 호출·조건 | 결과 | 호출자 처리 |
| --- | --- | --- |
| `canPlace`, `tryPlace`: 경계·점유 충돌 | `false`, 보드 무변경 | 이동·회전 후보 거절 또는 생성 불가 처리 |
| `dropDistance`: 이미 착지 | `0` | 추가 낙하 점수 없음 |
| `clearFullLines`: 완성 행 없음 | `0` | 줄 삭제 점수 없음 |
| `Board(rows, cols)`: 0 이하 크기 | `IllegalArgumentException` | 구성·테스트 입력 수정 |
| `Board.cellAt`: 범위 밖 직접 조회 | `IndexOutOfBoundsException` | 조회 좌표 수정. 빈 칸으로 대체하지 않음 |
| `fromId`: -1 또는 0~6 밖의 값 | `InvalidTetrominoIdException` | 빈 칸은 먼저 분기하고 잘못된 ID 유입 조사 |
| `dropDistance`: 시작 위치 자체가 충돌 | `InvalidPlacementException` | 엔진의 현재 블록·호출 순서 조사 |
| `Tetromino` 생성·이동·회전: 좌표 덧셈 오버플로 | `ArithmeticException` | 좌표 계산 오류 수정 |
| 팔레트: 종류 누락·RGB 범위 오류·종류끼리 또는 빈 칸과 색상 중복 | `InvalidColorSchemeException` | 팔레트 정의 수정 |
| 필수 참조가 null | `NullPointerException` | 초기화·호출 오류 수정 |
| 주입 난수원 자체의 실패 | 원래 예외 전파 | 실패를 숨기거나 임의 종류로 대체하지 않음 |

null 금지는 블록의 종류·회전, 계산 대상 보드·블록, 배치 블록, 난수원, 팔레트 맵·키·값과 `rgbOf` 종류에 적용된다. 아래 스냅샷 예제의 nullable 현재 블록은 엔진 보조 메서드의 규약이며 block API에 null을 전달하는 예외가 아니다.

커스텀 예외 3개는 모두 `IllegalArgumentException`의 하위 타입인 unchecked 예외다.

| 타입 | 생성자 | 진단 문맥 |
| --- | --- | --- |
| `InvalidTetrominoIdException` | `(int id)` | `int id()` |
| `InvalidColorSchemeException` | `(String message)` | 상위 타입의 `getMessage()` |
| `InvalidPlacementException` | `(Tetromino piece, int rows, int cols)` | `Tetromino piece()`, `int rows()`, `int cols()` |

일반 연동에서는 예외를 직접 생성할 필요가 없다. 전체 메시지 문자열을 분기 조건으로 사용하지 않는다.

기술적 실패의 최종 처리는 엔진·애플리케이션 경계의 책임이다. 해당 세션의 진행을 중단하고 문맥을 한 번 기록한 후, UI 안내·재시작 경로를 정하는 방안을 인계한다. 이는 아직 구현된 엔진 기능이 아니다. `Error`나 `Throwable`을 포괄적으로 잡아 정상 게임 흐름으로 바꾸지 않는다.

현재 공용 `GameEventListener`에는 기술적 오류 이벤트가 없다. 기술적 실패를 `onGameOver`로 위장하거나 담당자 1이 임의로 공용 이벤트를 추가하지 않는다. 전달 방식이 필요하면 담당자 2·B와 별도로 합의한다.

## 5. 엔진 호출 순서와 연결 예제

다음 예제는 **상위 엔진에 적용할 연결 예시**이지 `block`이 게임 루프를 제공한다는 뜻이 아니다. Java 21 기준이며 block 타입을 import해서 사용한다. 호출 중에는 같은 보드의 다른 변경이 없어야 한다.

### 5.1 세션 초기화·생성

```java
Board board = new Board();
TetrominoGenerator generator =
        new UniformTetrominoGenerator(new java.util.Random());
```

테스트에서는 시드가 있는 `new java.util.Random(seed)`나 제어 가능한 `RandomGenerator`를 주입할 수 있다. 실제 세션의 시드·수명 정책과 다음 큐는 엔진이 소유한다.

아래 메서드는 실제 점유 폭으로 가운데 정렬하는 기본안이다. 가로 여백이 홀수이면 왼쪽 여백을 한 칸 적게 둔다. 정확한 생성 위치는 담당자 2가 연결 시 확인한다.

```java
static Tetromino spawnCandidate(TetrominoType type, BoardView board) {
    Tetromino origin = new Tetromino(type, Rotation.SPAWN, 0, 0);
    var cells = origin.cells();
    int minRow = cells.stream().mapToInt(Cell::row).min().orElseThrow();
    int minCol = cells.stream().mapToInt(Cell::col).min().orElseThrow();
    int maxCol = cells.stream().mapToInt(Cell::col).max().orElseThrow();
    int width = maxCol - minCol + 1;
    int left = Math.floorDiv(board.cols() - width, 2);
    return origin.moveBy(-minRow, left - minCol);
}
```

이 방식에서 I는 기준 행 -1, 실제 점유 행 0으로 생성된다. 다른 종류도 실제 점유 셀의 최상단이 행 0이다. 반환값은 후보일 뿐이므로 `CollisionChecker.canPlace(board, candidate)`를 확인한 뒤 현재 블록으로 채택한다. 이 검사가 false인 생성 불가는 정상 게임 종료 조건이며 기술적 예외가 아니다.

### 5.2 이동·회전·일반 하강

```java
Tetromino candidate = current.moveBy(0, -1); // 왼쪽 이동 예시
if (CollisionChecker.canPlace(board, candidate)) {
    current = candidate; // 기존 값이 불변이므로 성공할 때만 교체한다.
}
```

오른쪽은 `moveBy(0, 1)`, 한 행 하강은 `moveBy(1, 0)`, 회전은 `rotateClockwise()`로 후보를 만든다. 좌우 이동·회전 실패 시 현재 블록을 유지한다. 하강 실패 시에는 엔진 정책에 따라 현재 블록을 고정하고 행을 삭제한다. 이동 성공마다 `tryPlace`를 호출하면 낙하 경로가 고정되므로 그렇게 사용하지 않는다.

자동·수동 하강 모두 실제로 이동한 행 수만 엔진 점수에 반영한다. 막힌 하강·회전·좌우 이동을 낙하 거리로 세지 않는다. 타이머 간격·가속·일시정지·입력 반복도 엔진 책임이다.

### 5.3 하드드롭·고정·행 삭제

```java
int distance = DropCalculator.dropDistance(board, current);
Tetromino landed = current.moveBy(distance, 0);
if (!board.tryPlace(landed)) {
    // 직렬화된 유효 상태에서 계산 직후 고정 실패는 엔진 상태 오류다.
    throw new IllegalStateException("Calculated landing could not be placed");
}
current = null; // 고정한 블록을 스냅샷에 다시 덧그리지 않는다.
int clearedLines = board.clearFullLines();
// 엔진이 distance와 clearedLines로 점수 갱신, 다음 생성 및 이벤트를 처리한다.
```

하드드롭 후 바로 고정하는 연결안이다. `distance`를 한 번만 반영하며, 이미 착지했다면 0이어도 고정·삭제는 수행한다. `block`은 줄 수 점수 공식·속도 증가를 계산하지 않는다.

권장 처리 순서는 후보 계산·검증 → 고정 → 현재 블록 해제 → 행 삭제 → 점수 갱신 → 다음 큐에서 새 블록 선택·생성 검사 → 큐 보충 → 정합성 있는 상태의 이벤트 전달이다. 생성 불가라면 정상 게임 종료로 분기한다. 이벤트를 언제·어떤 순서로 UI에 전달할지는 담당자 2·B가 맞춘다.

## 6. 스냅샷·UI·설정 연결

### 6.1 보드 스냅샷 합성

공용 타입은 `com.team.tetris.common.events.BoardSnapshot`이며 `BoardSnapshot(int[][] cells)`, `cells()`, `rows()`, `cols()`, `EMPTY`를 제공한다. 엔진은 고정 보드와 현재 낙하 블록을 합성해 UI에 전달한다.

아래는 엔진 보조 메서드 예시다. `current == null`은 아직 활성 블록이 없거나 고정 직후임을 뜻한다.

```java
static BoardSnapshot composeSnapshot(BoardView board, Tetromino current) {
    if (current != null && !CollisionChecker.canPlace(board, current)) {
        throw new IllegalStateException("Active piece overlaps fixed board or boundary");
    }
    int[][] cells = new int[board.rows()][board.cols()];
    for (int row = 0; row < board.rows(); row++) {
        for (int col = 0; col < board.cols(); col++) {
            cells[row][col] = board.cellAt(row, col)
                    .map(TetrominoType::id)
                    .orElse(BoardSnapshot.EMPTY);
        }
    }
    if (current != null) {
        for (Cell cell : current.cells()) {
            cells[cell.row()][cell.col()] = current.type().id();
        }
    }
    return new BoardSnapshot(cells);
}
```

`BoardSnapshot`은 record지만 현재 코드에서 내부 배열을 방어적으로 복사하지 않는다. 이벤트마다 바깥 배열과 각 행을 새로 만들고, 전달 이후 엔진·UI 어느 쪽도 그 배열을 변경하지 않는 소유권 규약이 필요하다. 동일 배열 재사용이나 얕은 복사만으로는 과거 스냅샷을 보호하지 못한다.

고정한 블록을 현재 블록으로 남겨 합성하면 중복 표현되고 행 삭제 후에는 잘못된 위치가 그려질 수 있다. 고정 후 현재 블록을 해제하거나 다음 유효 블록으로 교체한 뒤 합성한다.

### 6.2 이벤트와 미리보기

공용 `com.team.tetris.common.events.GameEventListener`의 시그니처는 다음과 같다.

| 메서드 | 엔진·UI 연결 내용 |
| --- | --- |
| `onBoardUpdated(BoardSnapshot snapshot)` | 고정 셀과 활성 블록이 합성된 종류 ID 배열 |
| `onScoreChanged(int score)` | 엔진이 계산한 점수 |
| `onNextBlocksChanged(int[] blockTypes)` | 다음 큐 순서의 종류 ID. RGB가 아님 |
| `onPauseStateChanged(boolean paused)` | 엔진의 일시정지 상태 |
| `onGameOver(int score)` | 정상 게임 종료 시 최종 점수 |

다음 큐 배열도 새 배열로 전달하고 이후 변경하지 않는 규약을 적용한다. UI는 미리보기 ID를 `TetrominoType.fromId(id)`로 바꾸고 SPAWN 형태를 조회해 그릴 수 있다. I의 로컬 선행 빈 행을 포함해 표시 영역에 맞춰 정렬한다. block에는 UI 전용 픽셀·미리보기 렌더링 API가 없다.

### 6.3 색상 모드

```java
ColorScheme scheme = colorBlindEnabled
        ? ColorScheme.colorBlind()
        : ColorScheme.standard();
int rgb = cellId == BoardSnapshot.EMPTY
        ? scheme.emptyRgb()
        : scheme.rgbOf(TetrominoType.fromId(cellId));
```

`colorBlindEnabled`와 `cellId`는 각각 설정 값과 표시할 셀 ID다. AWT·Swing 색상 객체 변환은 UI에서 수행한다. 보드와 다음 블록 미리보기 모두 같은 모드를 사용하며 설정 변경 시 다시 그린다.

설정의 ON/OFF·초기화·저장·재실행 복원은 담당자 2의 책임이다. B는 색상 외 문자·무늬를 병행하고 J/L·S/Z 등을 실제 크기·배경에서 확인한다. 적록·청황 색각 시뮬레이션과 실제 화면 검증은 아직 별도 작업이며 RGB 유일성 테스트로 대체하지 않는다.

## 7. 연동 작업 및 계약 변경

현재 구현된 ID·형태·회전·팔레트는 이 문서의 고정값으로 인계한다. 예전 계획 단계의 미정 항목과 혼동하지 않는다. 생성 정렬·세션 수명·이벤트 시점·기술적 오류 전달 등 상위 모듈 정책은 실제 연결 과정에서 담당자 2·B가 확인한다.

API 시그니처, ID, 좌표 해석, 회전, 예외 조건 또는 스냅샷 표현을 바꾸려면 다음을 함께 처리한다.

1. 담당자 1과 영향을 받는 담당자가 변경 전후 동작·호환성·연동 위치를 확인한다.
2. 소유 담당자가 코드와 회귀 테스트를 갱신하고 이 계약을 수정한다.
3. 담당자 1의 내부 설계·검증 이력에 영향이 있으면 개발 문서도 갱신한다.
4. 요구사항 해석·평가 기준의 모호함은 e-class 확인 대상으로 남기며 임의 결정을 공식 답변으로 기록하지 않는다.

불일치 보고에는 호출 API, 보드 크기·고정 셀, 종류·회전·기준점, 기대 결과·실제 반환/예외와 재현 테스트를 포함한다. 난수 관련 문제라면 난수원·시드를 함께 남긴다.

로컬 block 검증 결과와 재실행 명령은 [개발 문서의 테스트·검증](block_implementation_plan.md#6-테스트-구성과-품질-기준)을 참고한다. 독립 도메인 테스트 통과가 엔진·UI·Windows·NFR의 완료를 뜻하지는 않는다.

## 8. 연동 확인 목록

아래 미완료 표시는 계약 내용을 아직 구현하지 않았다는 뜻이 아니라, 타 모듈과의 실제 연결을 확인해야 한다는 뜻이다.

- [x] 담당자 1: 공개 API·ID·좌표·형태·팔레트·실패 조건을 현재 구현과 맞춰 인계했다.
- [ ] 담당자 2: 생성 위치, 다음 큐, 입력·타이머, 하강 점수·행 점수, 고정·삭제·다음 생성·정상 종료를 연결한다.
- [ ] 담당자 2: 새 세션 초기화와 기술적 실패의 중단·기록·재시작 흐름을 검증한다.
- [ ] 담당자 2·B: 스냅샷 합성·배열 소유권, 종류 ID 해석, 이벤트 순서와 UI 실행 스레드 경계를 확인한다.
- [ ] 담당자 2·B: 보드·미리보기 모드 전환과 설정 초기화·저장·재실행 복원을 확인한다.
- [ ] 담당자 2·B: 공용 오류 이벤트가 없는 상태에서 기술적 실패를 전달할 방법을 합의한다.
- [ ] B: 문자·무늬, 실제 배경·크기의 J/L·S/Z 구분, 적록·청황 색각 식별성을 확인한다.
- [ ] D: 전체 커버리지 기준·비활성 검증 설정, NFR 테스트, Windows 11·최소 사양·패키징을 검증한다.
