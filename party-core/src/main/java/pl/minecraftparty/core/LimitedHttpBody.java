package pl.minecraftparty.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;

/** Bounds allocation while receiving the body, including chunked responses without Content-Length. */
public final class LimitedHttpBody implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private Flow.Subscription subscription;
    public LimitedHttpBody(int limit) {
        if (limit < 1 || limit > 64 * 1024 * 1024) throw new IllegalArgumentException("body limit");
        this.limit = limit;
    }
    public CompletionStage<byte[]> getBody() { return result; }
    public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
    public void onNext(List<ByteBuffer> buffers) {
        if (result.isDone()) return;
        for (ByteBuffer buffer : buffers) {
            int count = buffer.remaining();
            if (count > limit - bytes.size()) {
                subscription.cancel(); result.completeExceptionally(new IOException("HTTP body too large")); return;
            }
            byte[] chunk = new byte[count]; buffer.get(chunk); bytes.writeBytes(chunk);
        }
        subscription.request(1);
    }
    public void onError(Throwable error) { result.completeExceptionally(error); }
    public void onComplete() { result.complete(bytes.toByteArray()); }
}
