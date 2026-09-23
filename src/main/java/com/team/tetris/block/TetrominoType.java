package com.team.tetris.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 일곱 블록의 전달 ID와 불변 형태 정의.
 *
 * ID는 enum 선언 순서와 독립적인 명시적 값이다. 엔진은 빈 칸을 별도로 처리한
 * 뒤 이 ID를 스냅샷 및 다음 블록 이벤트에 사용한다. 형태의 초기 방향은 과제
 * 참조 코드에 맞추되 I는 4×4, O는 2×2, 나머지는 3×3의 고정 격자에서 회전한다.
 * 빈 여백을 회전마다 잘라내지 않으므로 회전 중 기준점이 이동하지 않는다.
 *
 * @see "참조 초기 형태: https://github.com/Jindae/SeoulTech-SE-Tetris-Ref/tree/2e9ce46ae447f37475489687efae87ab74da084a/src/seoultech/se/tetris/blocks"
 */
public enum TetrominoType {
    /** 가로 네 칸. 4×4 격자에서 초기 점유 행은 1이다. */
    I(0, 4, new Cell(1, 0), new Cell(1, 1), new Cell(1, 2), new Cell(1, 3)),
    /** 2×2 정사각형. 회전해도 점유 셀과 기준점이 유지된다. */
    O(1, 2, new Cell(0, 0), new Cell(0, 1), new Cell(1, 0), new Cell(1, 1)),
    /** 초기 형태: 010 / 111. */
    T(2, 3, new Cell(0, 1), new Cell(1, 0), new Cell(1, 1), new Cell(1, 2)),
    /** 초기 형태: 011 / 110. */
    S(3, 3, new Cell(0, 1), new Cell(0, 2), new Cell(1, 0), new Cell(1, 1)),
    /** 초기 형태: 110 / 011. */
    Z(4, 3, new Cell(0, 0), new Cell(0, 1), new Cell(1, 1), new Cell(1, 2)),
    /** 참조 코드의 J 방향: 111 / 001. */
    J(5, 3, new Cell(0, 0), new Cell(0, 1), new Cell(0, 2), new Cell(1, 2)),
    /** 참조 코드의 L 방향: 111 / 100. */
    L(6, 3, new Cell(0, 0), new Cell(0, 1), new Cell(0, 2), new Cell(1, 0));

    private final int id;
    private final List<List<Cell>> shapes;

    TetrominoType(int id, int gridSize, Cell... initialCells) {
        this.id = id;
        List<List<Cell>> rotations = new ArrayList<>(4);
        List<Cell> current = List.of(initialCells);
        for (int turn = 0; turn < 4; turn++) {
            rotations.add(current);
            // 행이 아래로 증가하므로 (r,c) -> (c,N-1-r)이 시계 방향 회전이다.
            // O는 같은 셀 순서까지 유지해 불필요한 형태 변화를 만들지 않는다.
            if (gridSize != 2) {
                current = current.stream()
                        .map(cell -> new Cell(cell.col(), gridSize - 1 - cell.row()))
                        .toList();
            }
        }
        this.shapes = List.copyOf(rotations);
    }

    /**
     * enum의 선언 순서와 독립적인 전달 ID를 조회한다.
     * @return 스냅샷·미리보기에서 사용할 고정 ID(0~6)
     */
    public int id() {
        return id;
    }

    /**
     * 전달 ID를 도메인 종류로 변환한다. 빈 칸 ID(-1)는 블록 종류가 아니다.
     *
     * @param id 변환할 종류 ID
     * @return 대응하는 블록 종류
     * @throws InvalidTetrominoIdException ID가 0~6 중 하나가 아니면 발생
     */
    public static TetrominoType fromId(int id) {
        for (TetrominoType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new InvalidTetrominoIdException(id);
    }

    /** 외부에는 형태 테이블 대신 Tetromino의 절대 셀 조회만 공개한다. */
    List<Cell> offsets(Rotation rotation) {
        // ordinal은 내부의 네 회전 슬롯에만 사용하고 외부 전달 ID로 쓰지 않는다.
        return shapes.get(Objects.requireNonNull(rotation, "rotation").ordinal());
    }
}
