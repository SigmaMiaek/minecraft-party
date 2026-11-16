package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses numeric test candidates only; never attempts an external connection. */
class InternetExposureTest {
    private Invite invitation(UUID id, String secret, String fingerprint) {
        return new Invite(id, List.of("192.168.1.5", "8.8.8.8"), 34567, fingerprint, secret, System.currentTimeMillis() + 60000);
    }
    @Test void directPublicInviteUsesOnlyPublicCandidateAndCurrentRotatedSecret() throws Exception {
        var original = invitation(UUID.randomUUID(), Secrets.token(), "a".repeat(64));
        try (var exposure = InternetExposure.open(original, () -> false, value -> fail("No mapping should be needed"))) {
            var rotated = invitation(original.partyId(), Secrets.token(), original.fingerprint());
            Invite published = exposure.invitation(rotated);
            assertEquals(List.of("8.8.8.8"), published.candidates()); assertEquals(34567, published.port());
            assertEquals(rotated.secret(), published.secret()); assertNotEquals(original.secret(), published.secret());
            assertEquals(rotated.fingerprint(), published.fingerprint()); assertEquals(published, Invite.decode(published.encode()));
        }
    }
    @Test void exposureCannotBeReusedForAnotherHostOrAfterClose() throws Exception {
        var original = invitation(UUID.randomUUID(), Secrets.token(), "a".repeat(64));
        var exposure = InternetExposure.open(original, () -> false, value -> {});
        assertThrows(IllegalArgumentException.class, () -> exposure.invitation(invitation(UUID.randomUUID(), Secrets.token(), original.fingerprint())));
        assertThrows(IllegalArgumentException.class, () -> exposure.invitation(invitation(original.partyId(), Secrets.token(), "b".repeat(64))));
        exposure.close(); exposure.close();
        assertThrows(IllegalStateException.class, () -> exposure.invitation(original));
    }
    @Test void cancellationStopsEvenTheDirectPublicPath() {
        var original = invitation(UUID.randomUUID(), Secrets.token(), "a".repeat(64));
        assertThrows(IOException.class, () -> InternetExposure.open(original, () -> true, value -> {}));
    }
}
