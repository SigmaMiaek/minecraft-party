package pl.minecraftparty.network;

/** Unverified directory advertisement; contains no address, invitation secret or owner credential. */
public record PublicParty(String code, String name, String host, int players, int limit, boolean inWorld) {
    public PublicParty {
        if (code == null || !code.matches("[0-9]{6}") || !validName(name)
                || host == null || !host.matches("[A-Za-z0-9_]{1,16}")
                || limit < 2 || limit > 8 || players < 1 || players > limit) throw new IllegalArgumentException("public lobby");
    }
    public boolean full() { return players >= limit; }
    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.length() <= 40 && name.codePoints().noneMatch(c -> Character.isISOControl(c)
                || Character.getType(c) == Character.FORMAT || Character.getType(c) == Character.SURROGATE || c == 0xA7);
    }
    @Override public String toString() { return "PublicParty[REDACTED]"; }
}
