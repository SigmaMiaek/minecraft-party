package pl.minecraftparty.core;

import java.io.*;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import java.util.*;

final class SocketPipe {
    // Retain the transport separately: SSLSocket.close() can wait for a writer's
    // close_notify lock. Aborting TCP first releases blocked reads/writes on Windows.
    private static final Map<Socket, Socket> transports = Collections.synchronizedMap(new WeakHashMap<>());
    private SocketPipe() { }
    static void track(Socket tls, Socket transport) {
        if (tls == transport) throw new IllegalArgumentException("layered socket required");
        transports.put(Objects.requireNonNull(tls), Objects.requireNonNull(transport));
    }
    record TlsConnection(SSLSocket socket) implements AutoCloseable {
        @Override public void close() { SocketPipe.close(socket); }
    }
    static void transfer(Socket a, Socket b) throws InterruptedException {
        Thread reverse = Thread.ofVirtual().name("party-pipe-reverse").start(() -> pump(b, a));
        try { pump(a, b); reverse.join(); }
        finally { close(a); close(b); reverse.interrupt(); }
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
