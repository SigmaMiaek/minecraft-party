package pl.minecraftparty.core.upnp;

/** Conservative IPv4 filter; an eligible address is not proof of inbound reachability. */
public final class PublicAddress {
    private PublicAddress() { }
    public static boolean ipv4(String value) {
        if (value == null || !value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        String[] parts = value.split("\\."); int[] n = new int[4];
        for (int i = 0; i < 4; i++) { n[i] = Integer.parseInt(parts[i]); if (n[i] > 255) return false; }
        int a = n[0], b = n[1], c = n[2];
        return !(a == 0 || a == 10 || a == 127 || a >= 224
                || a == 100 && b >= 64 && b <= 127
                || a == 169 && b == 254 || a == 172 && b >= 16 && b <= 31
                || a == 192 && (b == 168 || b == 0 && (c == 0 || c == 2))
                || a == 198 && (b == 18 || b == 19 || b == 51 && c == 100)
                || a == 203 && b == 0 && c == 113);
    }
}
