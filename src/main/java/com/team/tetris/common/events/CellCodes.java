/*
 * 보드 스냅샷·미리보기 배열의 셀 코드 규칙. 일반 블록 0..6은 그대로 두고, 아이템은 표시 문자로 인코딩한다
 * (코드 = 블록 ID + 8 × 문자, 미리보기는 칸 번호를 추가). 게임과 화면이 함께 쓰는 유일한 인코딩이다.
 */
package com.team.tetris.common.events;

/**
 * 보드 스냅샷과 미리보기 배열에 쓰는 셀 코드. 게임과 화면이 함께 쓰는 유일한 인코딩 규칙이다.
 *
 * 일반 블록은 기존 ID {@code 0..6}을 그대로 쓰고 빈칸은 {@link BoardSnapshot#EMPTY}(-1)이다.
 * 아이템은 아이템의 표시 문자로 인코딩한다: {@code code = baseType + 8 × symbol}.
 * 아이템마다 문자가 다르므로(2차 요구사항 4.2) 새 아이템에 ID 범위를 따로 배정할 필요가 없고,
 * 화면은 아이템 종류를 몰라도 문자를 꺼내 그릴 수 있다.
 *
 * 미리보기 항목은 아이템이 붙은 칸 번호를 한 자리 더 얹는다: {@code code + 8 × 65536 × (cellIndex + 1)}.
 * 칸 번호 {@link #NO_INDEX}(-1)는 블록 전체가 아이템(예: 무게추)이거나 보드 위의 아이템 칸을 뜻한다.
 */
public final class CellCodes {
    /** 아이템이 없는 일반 셀의 문자 자리 값. */
    public static final char NO_SYMBOL = 0;
    /** 아이템이 특정 칸에 붙어 있지 않을 때의 칸 번호. */
    public static final int NO_INDEX = -1;

    private static final int MAX_BASE_TYPE = 6;
    private static final int BASE_RADIX = 8;
    private static final int SYMBOL_RADIX = Character.MAX_VALUE + 1;
    private static final int INDEX_RADIX = BASE_RADIX * SYMBOL_RADIX;
    private static final int MAX_CELL_INDEX = 3;

    private CellCodes() { }

    /** 보드 위 한 칸의 코드. {@code symbol}이 {@link #NO_SYMBOL}이면 일반 블록 ID와 같다. */
    public static int cell(int baseType, char symbol) {
        requireBaseType(baseType);
        if (symbol != NO_SYMBOL && !isSymbol(symbol)) {
            throw new IllegalArgumentException("Item symbol must be a visible character: " + (int) symbol);
        }
        return baseType + BASE_RADIX * symbol;
    }

    /**
     * 미리보기 한 항목의 코드. 포함형 아이템은 붙은 칸 번호(0..3)를, 블록형 아이템과 일반 블록은
     * {@link #NO_INDEX}를 넘긴다.
     */
    public static int preview(int baseType, char symbol, int cellIndex) {
        if (cellIndex < NO_INDEX || cellIndex > MAX_CELL_INDEX) {
            throw new IllegalArgumentException("cellIndex must be -1..3: " + cellIndex);
        }
        if (cellIndex != NO_INDEX && symbol == NO_SYMBOL) {
            throw new IllegalArgumentException("A cell index needs an item symbol");
        }
        return cell(baseType, symbol) + INDEX_RADIX * (cellIndex + 1);
    }

    /** 이 규칙으로 만들 수 있는 코드인지 확인한다. 빈칸(-1)과 옛 ID 체계의 값은 false다. */
    public static boolean isValid(int code) {
        if (code < 0) return false;
        int symbol = code / BASE_RADIX % SYMBOL_RADIX;
        int cellIndex = code / INDEX_RADIX - 1;
        if (code % BASE_RADIX > MAX_BASE_TYPE) return false;
        if (symbol != NO_SYMBOL && !isSymbol((char) symbol)) return false;
        if (cellIndex > MAX_CELL_INDEX) return false;
        return cellIndex == NO_INDEX || symbol != NO_SYMBOL;
    }

    /** 색을 빌려 올 블록 종류의 ID(0..6). */
    public static int baseType(int code) {
        requireValid(code);
        return code % BASE_RADIX;
    }

    /** 칸에 그릴 아이템 문자. 일반 블록이면 {@link #NO_SYMBOL}. */
    public static char symbol(int code) {
        requireValid(code);
        return (char) (code / BASE_RADIX % SYMBOL_RADIX);
    }

    public static boolean hasItem(int code) {
        return symbol(code) != NO_SYMBOL;
    }

    /** 미리보기에서 아이템이 붙은 칸 번호. 보드 셀·블록형 아이템·일반 블록은 {@link #NO_INDEX}. */
    public static int itemCellIndex(int code) {
        requireValid(code);
        return code / INDEX_RADIX - 1;
    }

    /** 아이템 문자로 쓸 수 있는 문자인지 확인한다. 공백·제어 문자는 화면에서 구분되지 않아 거부한다. */
    public static boolean isSymbol(char symbol) {
        return symbol != NO_SYMBOL && !Character.isWhitespace(symbol) && !Character.isISOControl(symbol);
    }

    private static void requireBaseType(int baseType) {
        if (baseType < 0 || baseType > MAX_BASE_TYPE) {
            throw new IllegalArgumentException("baseType must be a tetromino ID 0..6: " + baseType);
        }
    }

    private static void requireValid(int code) {
        if (!isValid(code)) throw new IllegalArgumentException("Not a cell code: " + code);
    }
}
