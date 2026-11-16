package pl.minecraftparty.core;

import java.util.Locale;

/** isVirtual() only detects subinterfaces in Java; common VPN adapters require an explicit exclusion too. */
public final class InterfaceSelection {
    private InterfaceSelection() { }
    public static boolean eligible(String name, String displayName, boolean pointToPoint, boolean subinterface) {
        String id = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String display = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (pointToPoint || subinterface) return false;
        if (id.matches("(?:ham|tun|tap|utun|wg|zt|tailscale|veth|virbr|vboxnet|vmnet)[0-9a-z_.:-]*")) return false;
        if (id.equals("docker0")) return false;
        return !(display.contains("hamachi") || display.contains("radmin") || display.contains("wireguard")
                || display.contains("tailscale") || display.contains("zerotier") || display.contains("tap-windows")
                || display.contains("openvpn") || display.contains("virtualbox") || display.contains("vmware"));
    }
}
