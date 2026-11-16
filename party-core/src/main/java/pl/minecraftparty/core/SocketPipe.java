package pl.minecraftparty.core;

import java.io.*;
import java.net.Socket;

final class SocketPipe {
    private SocketPipe() { }
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
        if (object != null) try { object.close(); } catch (Exception ignored) { }
    }
}
