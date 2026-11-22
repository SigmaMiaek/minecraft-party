package pl.minecraftparty.core;

import java.io.*;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import java.util.*;

final class SocketPipe {
    // Retain the transport separately: SSLSocket.close() can wait for a writer's
    // close_notify lock. Aborting TCP first releases blocked reads/writes on Windows.
    private static final Map<Socket, Socket> transports = Collections.synchronizedMap(new WeakHashMap<>());
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");
    private SocketPipe() { }
    static void track(Socket tls, Socket transport) {
        if (tls == transport) throw new IllegalArgumentException("layered socket required");
        transports.put(Objects.requireNonNull(tls), Objects.requireNonNull(transport));
    }
    record TlsConnection(SSLSocket socket) implements AutoCloseable {
        @Override public void close() { SocketPipe.close(socket); }
    }
    static void transfer(Socket a, Socket b) throws InterruptedException {
        // Bulk virtual-thread socket writes stall on the Windows loopback path.
        // Only authenticated world streams reach here (at most one per member).
        Thread reverse = startPump("party-pipe-reverse", () -> pump(b, a));
        Thread forward = null;
        try {
            if (WINDOWS) { forward = startPump("party-pipe-forward", () -> pump(a, b)); forward.join(); }
            else pump(a, b);
            reverse.join();
        } finally {
            close(a); close(b); reverse.interrupt();
            if (forward != null) forward.interrupt();
        }
    }
    private static Thread startPump(String name, Runnable task) {
        return WINDOWS ? Thread.ofPlatform().daemon().name(name).start(task) : Thread.ofVirtual().name(name).start(task);
    }
    private static void pump(Socket from, Socket to) {
        try {
            byte[] buffer = new byte[32768];
            InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream();
            int read;
            while ((read = in.read(buffer)) != -1) { out.write(buffer, 0, read); out.flush(); }
            to.shutdownOutput();
        } catch (IOException e) { close(from); close(to); }
    }
    static void close(AutoCloseable object) {
        if (object instanceof Socket socket) {
            Socket transport = transports.remove(socket);
            if (transport != null) try { transport.close(); } catch (IOException ignored) { }
        }
        if (object != null) try { object.close(); } catch (Exception ignored) { }
    }
}
