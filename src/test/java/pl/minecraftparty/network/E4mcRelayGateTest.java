package pl.minecraftparty.network;

import io.netty.buffer.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.junit.jupiter.api.Test;
import pl.minecraftparty.core.E4mcProtocol;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Real Netty decoder with fragmented bytes. No network sockets, native loading or e4mc service calls. */
class E4mcRelayGateTest {
    private static final String HOST = "example-test.de.e4mc.link";
    private EmbeddedChannel channel() {
        var channel = new EmbeddedChannel();
        channel.pipeline().addLast("preface-timeout", new ReadTimeoutHandler(10));
        channel.pipeline().addLast("gate", new E4mcRelay.RoutingGate(() -> HOST));
        return channel;
    }
    private byte[] drain(EmbeddedChannel channel) {
        var bytes = new ByteArrayOutputStream(); ByteBuf next;
        while ((next = channel.readInbound()) != null) {
            byte[] chunk = new byte[next.readableBytes()]; next.readBytes(chunk); next.release(); bytes.writeBytes(chunk);
        }
        return bytes.toByteArray();
    }
    @Test void routingPrefaceIsRemovedWithoutLosingTheFirstTlsRecord() throws Exception {
        var channel = channel(); try {
            byte[] tls = new byte[]{22,3,3,0,3,1,2,3};
            channel.writeInbound(Unpooled.wrappedBuffer(E4mcProtocol.routingPreface(HOST), tls));
            assertArrayEquals(tls, drain(channel)); assertNull(channel.pipeline().get("gate"));
            assertNull(channel.pipeline().get("preface-timeout"));
        } finally { channel.finishAndReleaseAll(); }
    }
    @Test void oneByteFragmentsDoNotLeakRoutingBytesIntoTheTlsListener() throws Exception {
        var channel = channel(); try {
            for (byte b : E4mcProtocol.routingPreface(HOST)) {
                channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{b})); assertArrayEquals(new byte[0], drain(channel));
            }
            byte[] tls = new byte[]{22,3,3,1,0};
            for (byte b : tls) channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{b}));
            assertArrayEquals(tls, drain(channel)); assertTrue(channel.isActive());
        } finally { channel.finishAndReleaseAll(); }
    }
    @Test void aRoutingPrefaceWithoutTlsBytesRearmsTheManuallyReadStream() throws Exception {
        var channel = channel(); var reads = new AtomicInteger();
        try {
            channel.config().setAutoRead(false);
            channel.pipeline().addFirst("read-requests", new ChannelOutboundHandlerAdapter() {
                @Override public void read(ChannelHandlerContext ctx) { reads.incrementAndGet(); ctx.read(); }
            });
            channel.writeInbound(Unpooled.wrappedBuffer(E4mcProtocol.routingPreface(HOST)));
            channel.runPendingTasks();
            assertTrue(channel.isActive());
            assertArrayEquals(new byte[0], drain(channel));
            assertTrue(reads.get() > 0, "After consuming only the routing preface, another read must receive the TLS ClientHello");
        } finally { channel.finishAndReleaseAll(); }
    }
    @Test void aWrongDestinationClosesTheStreamBeforeForwardingAnyData() throws Exception {
        var channel = channel(); try {
            channel.writeInbound(Unpooled.wrappedBuffer(E4mcProtocol.routingPreface("other.de.e4mc.link"), new byte[]{22,3,3}));
            assertFalse(channel.isActive()); assertArrayEquals(new byte[0], drain(channel));
        } finally { channel.finishAndReleaseAll(); }
    }
    @Test void oversizedNoncanonicalAndUnterminatedLengthsAreRejectedWithoutWaitingForPayload() {
        for (byte[] prefix : new byte[][]{{-127,4}, {-127,0}, {-128,-128,-128}}) {
            var channel = channel(); try {
                channel.writeInbound(Unpooled.wrappedBuffer(prefix)); assertFalse(channel.isActive());
                assertArrayEquals(new byte[0], drain(channel));
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}
