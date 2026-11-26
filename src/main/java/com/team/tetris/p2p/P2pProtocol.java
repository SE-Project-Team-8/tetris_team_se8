package com.team.tetris.p2p;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.common.events.CellPosition;
import com.team.tetris.game.versus.GarbageQueue;
import com.team.tetris.game.versus.MatchResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Versioned, length-prefixed messages. Remote data is decoded as bounded primitive fields. */
final class P2pProtocol {
    static final int SIGNATURE = 0x54455450; // TETP
    static final int VERSION = 3;
    private static final int MAX_MESSAGE_BYTES = 2048;
    private static final int ITEM_CELL = 9;
    private static final int ROWS = GameConstants.BOARD_ROWS;
    private static final int COLS = GameConstants.BOARD_COLS;
    private static final int CELL_COUNT = ROWS * COLS;
    private static final int MAX_BLAST_CELLS = 25;

    private P2pProtocol() { }

    sealed interface Message permits Mode, Ready, Start, Board, Attack, PauseRequest, Pause,
            TopOut, Stop, FinalScore, Result, Ping, Pong, Effect { }

    record Mode(int selection, VersusMode mode, int timeLimitMinutes) implements Message { }
    record Ready(int round, int selection) implements Message { }
    record Start(int round, VersusMode mode, Difficulty difficulty, int timeLimitMinutes) implements Message { }
    record Board(int round, P2pSession.BoardState state) implements Message { }
    record Attack(int round, List<GarbageLine> lines) implements Message { }
    record PauseRequest(int round) implements Message { }
    record Pause(int round, boolean paused) implements Message { }
    record TopOut(int round) implements Message { }
    record Stop(int round) implements Message { }
    record FinalScore(int round, int score) implements Message { }
    record Result(int round, MatchResult result) implements Message { }
    record Ping(long token) implements Message { }
    record Pong(long token) implements Message { }
    record Effect(int round, EffectData data) implements Message { }

    sealed interface EffectData permits LinesCleared, HardDropped, BombExploded, AnvilCrushed,
            FeverActivated, SlowDownActivated { }

    record LinesCleared(List<Integer> rows, BoardSnapshot boardBeforeClear) implements EffectData { }
    record HardDropped(BoardSnapshot fallingBefore, BoardSnapshot fallingAfter) implements EffectData { }
    record BombExploded(CellPosition center, List<CellPosition> cells) implements EffectData { }
    record AnvilCrushed(List<CellPosition> cells) implements EffectData { }
    record FeverActivated(int seconds) implements EffectData { }
    record SlowDownActivated(int previousLevel, int currentLevel) implements EffectData { }

    static void write(DataOutputStream wire, Message message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        switch (message) {
            case Mode m -> {
                out.writeByte(1);
                out.writeInt(m.selection());
                out.writeByte(m.mode().ordinal());
                out.writeByte(m.timeLimitMinutes());
            }
            case Ready m -> {
                writeHeader(out, 2, m.round());
                out.writeInt(m.selection());
            }
            case Start m -> {
                writeHeader(out, 3, m.round());
                out.writeByte(m.mode().ordinal());
                out.writeByte(m.difficulty().ordinal());
                out.writeByte(m.timeLimitMinutes());
            }
            case Board m -> {
                writeHeader(out, 4, m.round());
                writeBoard(out, m.state());
            }
            case Attack m -> {
                writeHeader(out, 5, m.round());
                writeLines(out, m.lines());
            }
            case PauseRequest m -> writeHeader(out, 6, m.round());
            case Pause m -> {
                writeHeader(out, 7, m.round());
                out.writeBoolean(m.paused());
            }
            case TopOut m -> writeHeader(out, 8, m.round());
            case Stop m -> writeHeader(out, 9, m.round());
            case FinalScore m -> {
                writeHeader(out, 10, m.round());
                out.writeInt(m.score());
            }
            case Result m -> {
                writeHeader(out, 11, m.round());
                MatchResult result = m.result();
                out.writeByte(result.winner() == null ? -1 : result.winner().ordinal());
                out.writeByte(result.reason().ordinal());
                out.writeInt(result.p1Score());
                out.writeInt(result.p2Score());
            }
            case Ping m -> {
                out.writeByte(12);
                out.writeLong(m.token());
            }
            case Pong m -> {
                out.writeByte(13);
                out.writeLong(m.token());
            }
            case Effect m -> {
                writeHeader(out, 14, m.round());
                writeEffect(out, m.data());
            }
        }
        bounded(bytes.size(), 1, MAX_MESSAGE_BYTES);
        wire.writeShort(bytes.size());
        bytes.writeTo(wire);
        wire.flush();
    }

    static Message read(DataInputStream wire) throws IOException {
        byte[] bytes = new byte[bounded(wire.readUnsignedShort(), 1, MAX_MESSAGE_BYTES)];
        wire.readFully(bytes);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        Message message;
        try {
            message = switch (in.readUnsignedByte()) {
                case 1 -> new Mode(readRound(in), readMode(in), readTimeLimitMinutes(in));
                case 2 -> new Ready(readRound(in), bounded(in.readInt(), 0, Integer.MAX_VALUE));
                case 3 -> new Start(readRound(in), readMode(in),
                        enumValue(Difficulty.values(), in.readUnsignedByte()), readTimeLimitMinutes(in));
                case 4 -> new Board(readRound(in), readBoard(in));
                case 5 -> new Attack(readRound(in), readLines(in, ROWS));
                case 6 -> new PauseRequest(readRound(in));
                case 7 -> new Pause(readRound(in), bounded(in.readUnsignedByte(), 0, 1) == 1);
                case 8 -> new TopOut(readRound(in));
                case 9 -> new Stop(readRound(in));
                case 10 -> new FinalScore(readRound(in), readScore(in));
                case 11 -> new Result(readRound(in), readResult(in));
                case 12 -> new Ping(in.readLong());
                case 13 -> new Pong(in.readLong());
                case 14 -> new Effect(readRound(in), readEffect(in));
                default -> throw new IOException("Unknown P2P message");
            };
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid P2P message", invalid);
        }
        if (in.available() != 0) throw new IOException("Unexpected data in P2P message");
        return message;
    }

    private static void writeHeader(DataOutputStream out, int type, int round) throws IOException {
        out.writeByte(type);
        out.writeInt(round);
    }

    private static int readRound(DataInputStream in) throws IOException {
        return bounded(in.readInt(), 1, Integer.MAX_VALUE);
    }

    private static VersusMode readMode(DataInputStream in) throws IOException {
        return enumValue(VersusMode.values(), in.readUnsignedByte());
    }

    private static int readTimeLimitMinutes(DataInputStream in) throws IOException {
        return bounded(in.readUnsignedByte(), P2pSession.MIN_TIME_LIMIT_MINUTES, P2pSession.MAX_TIME_LIMIT_MINUTES);
    }

    private static void writeBoard(DataOutputStream out, P2pSession.BoardState frame) throws IOException {
        writeSnapshot(out, frame.board());
        out.writeByte(frame.landing().size());
        for (int position : frame.landing()) out.writeByte(position);
        out.writeInt(frame.next());
        out.writeInt(frame.score());
        out.writeInt(frame.level());
        writeLines(out, frame.queue());
    }

    private static P2pSession.BoardState readBoard(DataInputStream in) throws IOException {
        BoardSnapshot board = readSnapshot(in);
        int ghostCount = bounded(in.readUnsignedByte(), 0, CELL_COUNT);
        List<Integer> landing = new ArrayList<>(ghostCount);
        for (int i = 0; i < ghostCount; i++) {
            landing.add(bounded(in.readUnsignedByte(), 0, CELL_COUNT - 1));
        }
        int next = in.readInt();
        if (next != BoardSnapshot.EMPTY && !CellCodes.isValid(next)) throw new IOException("Invalid preview code");
        int score = readScore(in);
        int level = bounded(in.readInt(), 1, Integer.MAX_VALUE);
        return new P2pSession.BoardState(board, List.copyOf(landing), next, score, level,
                readLines(in, GarbageQueue.CAPACITY));
    }

    private static void writeEffect(DataOutputStream out, EffectData effect) throws IOException {
        switch (effect) {
            case LinesCleared e -> {
                out.writeByte(1);
                out.writeByte(e.rows().size());
                for (int row : e.rows()) out.writeByte(row);
                writeSnapshot(out, e.boardBeforeClear());
            }
            case HardDropped e -> {
                out.writeByte(2);
                writeFalling(out, e.fallingBefore());
                writeFalling(out, e.fallingAfter());
            }
            case BombExploded e -> {
                out.writeByte(3);
                writePosition(out, e.center());
                writePositions(out, e.cells());
            }
            case AnvilCrushed e -> {
                out.writeByte(4);
                writePositions(out, e.cells());
            }
            case FeverActivated e -> {
                out.writeByte(5);
                out.writeInt(e.seconds());
            }
            case SlowDownActivated e -> {
                out.writeByte(6);
                out.writeInt(e.previousLevel());
                out.writeInt(e.currentLevel());
            }
        }
    }

    private static EffectData readEffect(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> {
                int count = bounded(in.readUnsignedByte(), 1, ROWS);
                List<Integer> rows = new ArrayList<>(count);
                for (int i = 0; i < count; i++) rows.add(bounded(in.readUnsignedByte(), 0, ROWS - 1));
                yield new LinesCleared(List.copyOf(rows), readSnapshot(in));
            }
            case 2 -> new HardDropped(readFalling(in), readFalling(in));
            case 3 -> new BombExploded(readPosition(in), readPositions(in, 0, MAX_BLAST_CELLS));
            case 4 -> new AnvilCrushed(readPositions(in, 1, CELL_COUNT));
            case 5 -> new FeverActivated(bounded(in.readInt(), 1, Integer.MAX_VALUE));
            case 6 -> {
                int previousLevel = bounded(in.readInt(), 1, Integer.MAX_VALUE);
                yield new SlowDownActivated(previousLevel, bounded(in.readInt(), 1, previousLevel));
            }
            default -> throw new IOException("Unknown P2P effect");
        };
    }

    private static void writeSnapshot(DataOutputStream out, BoardSnapshot snapshot) throws IOException {
        for (int[] row : snapshot.cells()) {
            for (int code : row) writeCell(out, code);
        }
    }

    private static BoardSnapshot readSnapshot(DataInputStream in) throws IOException {
        int[][] cells = new int[ROWS][COLS];
        for (int[] row : cells) {
            for (int col = 0; col < COLS; col++) row[col] = readCell(in);
        }
        return new BoardSnapshot(cells);
    }

    private static void writeFalling(DataOutputStream out, BoardSnapshot snapshot) throws IOException {
        List<Integer> occupied = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (snapshot.cells()[row][col] != BoardSnapshot.EMPTY) occupied.add(row * COLS + col);
            }
        }
        out.writeByte(occupied.size());
        for (int position : occupied) {
            out.writeByte(position);
            writeCell(out, snapshot.cells()[position / COLS][position % COLS]);
        }
    }

    private static BoardSnapshot readFalling(DataInputStream in) throws IOException {
        int count = bounded(in.readUnsignedByte(), 1, CELL_COUNT);
        int[][] cells = new int[ROWS][COLS];
        for (int[] row : cells) Arrays.fill(row, BoardSnapshot.EMPTY);
        for (int i = 0; i < count; i++) {
            CellPosition position = readPosition(in);
            int[] row = cells[position.row()];
            int code = readCell(in);
            if (row[position.col()] != BoardSnapshot.EMPTY || code < 0) {
                throw new IOException("Invalid P2P falling cell");
            }
            row[position.col()] = code;
        }
        return new BoardSnapshot(cells);
    }

    private static void writePosition(DataOutputStream out, CellPosition position) throws IOException {
        out.writeByte(position.row() * COLS + position.col());
    }

    private static CellPosition readPosition(DataInputStream in) throws IOException {
        int position = bounded(in.readUnsignedByte(), 0, CELL_COUNT - 1);
        return new CellPosition(position / COLS, position % COLS);
    }

    private static void writePositions(DataOutputStream out, List<CellPosition> positions) throws IOException {
        out.writeByte(positions.size());
        for (CellPosition position : positions) writePosition(out, position);
    }

    private static List<CellPosition> readPositions(DataInputStream in, int min, int max) throws IOException {
        int count = bounded(in.readUnsignedByte(), min, max);
        List<CellPosition> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) positions.add(readPosition(in));
        return List.copyOf(positions);
    }

    private static void writeCell(DataOutputStream out, int code) throws IOException {
        // Empty, garbage and ordinary cells occupy a byte each. Item cells keep the original full code.
        if (code <= 6) {
            out.writeByte(code + 2);
        } else {
            out.writeByte(ITEM_CELL);
            out.writeInt(code);
        }
    }

    private static int readCell(DataInputStream in) throws IOException {
        int marker = bounded(in.readUnsignedByte(), 0, ITEM_CELL);
        return marker == ITEM_CELL ? readItemCell(in) : marker - 2;
    }

    private static int readItemCell(DataInputStream in) throws IOException {
        int code = in.readInt();
        if (!CellCodes.isValid(code) || !CellCodes.hasItem(code) || CellCodes.itemCellIndex(code) != CellCodes.NO_INDEX) {
            throw new IOException("Invalid item cell");
        }
        return code;
    }

    private static void writeLines(DataOutputStream out, List<GarbageLine> lines) throws IOException {
        out.writeByte(lines.size());
        for (GarbageLine line : lines) out.writeShort(line.cells());
    }

    private static List<GarbageLine> readLines(DataInputStream in, int max) throws IOException {
        int size = bounded(in.readUnsignedByte(), 0, max);
        List<GarbageLine> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) lines.add(new GarbageLine(in.readUnsignedShort()));
        return List.copyOf(lines);
    }

    private static MatchResult readResult(DataInputStream in) throws IOException {
        int player = in.readByte();
        Player winner = player == -1 ? null : enumValue(Player.values(), player);
        MatchResult.Reason reason = enumValue(MatchResult.Reason.values(), in.readUnsignedByte());
        return new MatchResult(winner, reason, readScore(in), readScore(in));
    }

    private static int readScore(DataInputStream in) throws IOException {
        return bounded(in.readInt(), 0, Integer.MAX_VALUE);
    }

    private static <T> T enumValue(T[] values, int index) throws IOException {
        return values[bounded(index, 0, values.length - 1)];
    }

    private static int bounded(int value, int min, int max) throws IOException {
        if (value < min || value > max) throw new IOException("P2P message field is out of range");
        return value;
    }
}
