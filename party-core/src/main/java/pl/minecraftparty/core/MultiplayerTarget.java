package pl.minecraftparty.core;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Locale;

/** The original Minecraft address, preserving DNS/SRV instead of a host-specific resolved IP. */
public record MultiplayerTarget(String address) {
    public MultiplayerTarget {
        if (address == null || address.isBlank() || address.length() > 255 || !address.equals(address.strip())
                || address.chars().anyMatch(c -> c <= 32 || Character.isISOControl(c) || Character.getType(c) == Character.FORMAT))
            throw new IllegalArgumentException("server address");
        String host, port = null;
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end < 2) throw new IllegalArgumentException("IPv6 address");
            host = address.substring(1, end);
            if (end + 1 < address.length()) {
                if (address.charAt(end + 1) != ':') throw new IllegalArgumentException("port");
                port = address.substring(end + 2);
            }
            // Hex literals only: this parse never performs a DNS query.
            if (!host.contains(":") || !host.matches("[0-9A-Fa-f:]+")) throw new IllegalArgumentException("IPv6 address");
            try {
                InetAddress literal = InetAddress.getByName(host);
                if (!(literal instanceof Inet6Address) || literal.isAnyLocalAddress() || literal.isLoopbackAddress()
                        || literal.isMulticastAddress() || literal.isLinkLocalAddress()) throw new IllegalArgumentException("IPv6 address");
            } catch (java.net.UnknownHostException e) { throw new IllegalArgumentException("IPv6 address", e); }
        } else {
            int colon = address.indexOf(':');
            host = colon < 0 ? address : address.substring(0, colon);
            if (colon >= 0) port = address.substring(colon + 1);
            String ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (ascii.isEmpty() || ascii.length() > 253) throw new IllegalArgumentException("hostname");
            String normalizedHost = ascii.replaceFirst("\\.$", "");
            if (normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost"))
                throw new IllegalArgumentException("loopback is local to each player");
            if (ascii.matches("[0-9.]+") && !ascii.matches("[0-9]+(?:\\.[0-9]+){3}"))
                throw new IllegalArgumentException("IPv4 address requires four octets");
            if (ascii.matches("[0-9]+(?:\\.[0-9]+){3}")) {
                for (String part : ascii.split("\\.")) if (part.length() > 3 || Integer.parseInt(part) > 255)
                    throw new IllegalArgumentException("IPv4 address");
                try {
                    InetAddress literal = InetAddress.getByName(ascii);
                    if (literal.isAnyLocalAddress() || literal.isLoopbackAddress() || literal.isMulticastAddress()
                            || literal.isLinkLocalAddress() || ascii.equals("255.255.255.255")) throw new IllegalArgumentException("IPv4 address");
                } catch (java.net.UnknownHostException e) { throw new IllegalArgumentException("IPv4 address", e); }
            }
            for (String label : ascii.replaceFirst("\\.$", "").split("\\.", -1)) {
                if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw new IllegalArgumentException("hostname");
            }
        }
        if (port != null) {
            if (!port.matches("[0-9]{1,5}")) throw new IllegalArgumentException("port");
            int number = Integer.parseInt(port);
            if (number < 1 || number > 65535) throw new IllegalArgumentException("port");
        }
    }
    @Override public String toString() { return "MultiplayerTarget[REDACTED]"; }
}
