package pl.minecraftparty.core;

import java.time.Instant;
import java.util.*;

/** Bounded diagnostics with no API accepting address, invitation, name or raw exception text. */
public final class ConnectionTrace {
    public enum Event { CONNECTING, CONNECTED, AUTHENTICATED, REJECTED, RETRYING, CANCELLED }
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    public synchronized void mark(int candidate, NetworkFailure.Phase phase, Event event) {
        append(candidate, phase, event.name());
    }
    public synchronized void failure(int candidate, NetworkFailure failure) {
        append(candidate, failure.phase(), failure.reason().name() + " (" + failure.exceptionType() + ")");
    }
    private void append(int candidate, NetworkFailure.Phase phase, String outcome) {
        lines.addLast(Instant.now() + " candidate=" + Math.clamp(candidate, 0, 8) + " " + phase + " " + outcome);
        while (lines.size() > 32) lines.removeFirst();
    }
    public synchronized String report() { return String.join("\n", lines); }
}
