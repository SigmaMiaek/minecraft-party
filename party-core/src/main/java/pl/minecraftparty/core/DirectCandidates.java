package pl.minecraftparty.core;

import java.net.*;
import java.util.*;

/** Direct candidates only. Does not claim to implement ICE, STUN or port mapping. */
public final class DirectCandidates {
    private DirectCandidates() { }
    public static List<String> gather() throws SocketException {
        List<String> result = new ArrayList<>();
        var interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            var network = interfaces.nextElement();
            if (!network.isUp() || network.isLoopback()) continue;
            if (!InterfaceSelection.eligible(network.getName(), network.getDisplayName(), network.isPointToPoint(), network.isVirtual())) continue;
            var addresses = network.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isAnyLocalAddress() && !address.isLinkLocalAddress()
                        && !address.isMulticastAddress() && !address.getHostAddress().contains("%")) result.add(address.getHostAddress());
            }
        }
        result.sort(Comparator.comparingInt(s -> s.contains(":") ? 1 : 0));
        return result.stream().distinct().limit(8).toList();
    }
}
