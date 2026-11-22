package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import java.net.Socket;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class SocketPipeTest {
    @Test void teardownAbortsTransportBeforeTlsEvenIfTransportCloseThrows() {
        for (boolean failing : new boolean[]{false, true}) {
            List<String> order = new ArrayList<>();
            Socket tls = new Socket() { @Override public void close() { order.add("tls"); } };
            Socket transport = new Socket() { @Override public void close() throws IOException {
                order.add("tcp"); if (failing) throw new IOException("closed");
            } };
            SocketPipe.track(tls, transport); SocketPipe.close(tls);
            assertEquals(List.of("tcp", "tls"), order);
            SocketPipe.close(tls);
            assertEquals(List.of("tcp", "tls", "tls"), order); // Tracking entry was retired.
        }
    }
}
