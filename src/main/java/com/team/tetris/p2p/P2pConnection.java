package com.team.tetris.p2p;

import java.awt.EventQueue;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.stream.Stream;

/** One TCP peer. Socket reads and writes run off the EDT, and callbacks run on it. */
final class P2pConnection implements AutoCloseable {
    private static final int OPEN_TIMEOUT_MILLIS = 5_000;
    private static final int QUEUE_LIMIT = 128;
    private static final int EDT_BATCH_SIZE = 16;

    interface Listener {
        void listening(String addresses);
        void connected();
        void message(P2pProtocol.Message message);
        void failed(String description);
    }

    private final Listener listener;
    private final ArrayDeque<P2pProtocol.Message> inbox = new ArrayDeque<>();
    private final ArrayDeque<P2pProtocol.Message> outbox = new ArrayDeque<>();
    private boolean dispatchScheduled;
    private boolean closed;
    private ServerSocket server;
    private Socket socket;

    P2pConnection(Listener listener) {
        this.listener = listener;
    }

    void open(InetAddress peer, int port) {
        Thread.startVirtualThread(() -> {
            try {
                Socket connected = peer == null ? accept(port) : connect(peer, port);
                exchange(connected);
            } catch (IOException failure) {
                fail(explain(failure));
            }
        });
    }

    private Socket accept(int port) throws IOException {
        try (ServerSocket waiting = new ServerSocket()) {
            synchronized (this) {
                checkOpen();
                server = waiting;
            }
            waiting.setReuseAddress(true);
            waiting.bind(new InetSocketAddress(port));
            String addresses = String.join(", ", localAddresses());
            EventQueue.invokeLater(() -> listener.listening(addresses));
            Socket accepted = waiting.accept();
            register(accepted);
            return accepted;
        } finally {
            synchronized (this) {
                server = null;
            }
        }
    }

    private Socket connect(InetAddress peer, int port) throws IOException {
        Socket connecting = new Socket();
        register(connecting);
        connecting.connect(new InetSocketAddress(peer, port), OPEN_TIMEOUT_MILLIS);
        return connecting;
    }

    private synchronized void register(Socket connected) throws IOException {
        if (closed) closeQuietly(connected);
        checkOpen();
        socket = connected;
    }

    private void exchange(Socket peer) throws IOException {
        peer.setTcpNoDelay(true);
        peer.setSoTimeout(OPEN_TIMEOUT_MILLIS);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(peer.getOutputStream()));
        DataInputStream in = new DataInputStream(new BufferedInputStream(peer.getInputStream()));
        out.writeInt(P2pProtocol.SIGNATURE);
        out.writeShort(P2pProtocol.VERSION);
        out.flush();
        if (in.readInt() != P2pProtocol.SIGNATURE || in.readUnsignedShort() != P2pProtocol.VERSION) {
            fail("상대의 P2P 게임 버전과 호환되지 않습니다.");
            return;
        }
        peer.setSoTimeout(0);
        EventQueue.invokeLater(listener::connected);
        Thread.startVirtualThread(() -> writeMessages(out));
        while (true) {
            enqueueReceived(P2pProtocol.read(in));
        }
    }

    private synchronized void enqueueReceived(P2pProtocol.Message message) throws IOException {
        checkOpen();
        if (!enqueue(inbox, message)) throw new IOException("P2P receive queue is full");
        if (!dispatchScheduled) {
            dispatchScheduled = true;
            EventQueue.invokeLater(this::dispatchReceived);
        }
    }

    private void dispatchReceived() {
        for (int count = 0; count < EDT_BATCH_SIZE; count++) {
            P2pProtocol.Message message;
            synchronized (this) {
                message = inbox.pollFirst();
                if (message == null) {
                    dispatchScheduled = false;
                    return;
                }
            }
            listener.message(message);
        }
        EventQueue.invokeLater(this::dispatchReceived);
    }

    void send(P2pProtocol.Message message) {
        synchronized (this) {
            if (closed) return;
            if (enqueue(outbox, message)) {
                notifyAll();
                return;
            }
        }
        fail("전송이 계속 지연되어 연결을 종료했습니다.");
    }

    private static boolean enqueue(ArrayDeque<P2pProtocol.Message> queue, P2pProtocol.Message message) {
        // Keep control messages and attacks in order. Only consecutive display states can replace each other.
        if (message instanceof P2pProtocol.Board && queue.peekLast() instanceof P2pProtocol.Board) queue.removeLast();
        if (queue.size() == QUEUE_LIMIT) return false;
        queue.addLast(message);
        return true;
    }

    private void writeMessages(DataOutputStream out) {
        try {
            P2pProtocol.Message message;
            while ((message = take()) != null) P2pProtocol.write(out, message);
        } catch (IOException failure) {
            fail(explain(failure));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("상대와의 연결이 종료되었습니다.");
        }
    }

    private synchronized P2pProtocol.Message take() throws InterruptedException {
        while (!closed && outbox.isEmpty()) wait();
        return outbox.pollFirst();
    }

    private void checkOpen() throws IOException {
        if (closed) throw new IOException("Connection was cancelled");
    }

    private void fail(String description) {
        if (shutdown()) EventQueue.invokeLater(() -> listener.failed(description));
    }

    @Override
    public void close() {
        shutdown();
    }

    private boolean shutdown() {
        ServerSocket previousServer;
        Socket previousSocket;
        synchronized (this) {
            if (closed) return false;
            closed = true;
            previousServer = server;
            previousSocket = socket;
            inbox.clear();
            outbox.clear();
            notifyAll();
        }
        closeQuietly(previousServer);
        closeQuietly(previousSocket);
        return true;
    }

    private static void closeQuietly(Closeable resource) {
        if (resource == null) return;
        try {
            resource.close();
        } catch (IOException ignored) {
            // The session has already been closed.
        }
    }

    private static String explain(IOException failure) {
        return switch (failure) {
            case BindException ignored -> "서버를 열 수 없습니다. 다른 게임 창이 포트를 사용 중인지 확인하세요.";
            case ConnectException ignored -> "접속할 수 없습니다. 상대 서버가 대기 중이고 IP가 맞는지 확인하세요.";
            case SocketTimeoutException ignored -> "접속 시간이 초과되었습니다. IP와 네트워크 상태를 확인하세요.";
            case EOFException ignored -> "상대와의 연결이 종료되었습니다.";
            default -> "상대와의 연결이 끊겼거나 전송 데이터에 문제가 있습니다.";
        };
    }

    static InetAddress parseIp(String input) throws UnknownHostException {
        String address = input.strip();
        if (address.length() > 80 || !address.contains(":") && !address.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
            throw new UnknownHostException("Expected an IPv4 or IPv6 address");
        }
        // Only numeric literals reach getByName. A colon selects IPv6 parsing without DNS.
        return InetAddress.getByName(address);
    }

    private static List<String> localAddresses() throws IOException {
        try (Stream<NetworkInterface> interfaces = NetworkInterface.networkInterfaces()) {
            List<String> reachable = interfaces.flatMap(NetworkInterface::inetAddresses)
                    .filter(address -> !address.isLoopbackAddress() && !address.isLinkLocalAddress())
                    .sorted((a, b) -> Boolean.compare(b instanceof Inet4Address, a instanceof Inet4Address))
                    .map(InetAddress::getHostAddress)
                    .distinct()
                    .toList();
            return reachable.isEmpty() ? List.of(InetAddress.getLoopbackAddress().getHostAddress()) : reachable;
        }
    }
}
