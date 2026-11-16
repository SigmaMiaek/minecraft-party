package pl.minecraftparty.core.upnp;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UpnpProtocolTest {
    private static final URI DESCRIPTION = URI.create("http://192.168.1.1:5000/root.xml");
    private static byte[] xml(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static String document(String base, String control) {
        return "<root xmlns='urn:schemas-upnp-org:device-1-0'>" + base + "<device><serviceList><service>"
                + "<serviceType>urn:schemas-upnp-org:service:WANIPConnection:2</serviceType><controlURL>" + control
                + "</controlURL></service></serviceList></device></root>";
    }
    @Test void discoveryAcceptsOnlyNumericLocalResponderLocation() throws Exception {
        InetAddress router = InetAddress.getByName("192.168.1.1");
        assertEquals(DESCRIPTION, UpnpProtocol.location("HTTP/1.1 200 OK\r\nLOCATION: " + DESCRIPTION + "\r\n\r\n", router));
        for (String uri : List.of("http://evil.example/root.xml", "http://8.8.8.8/root.xml", "file:///etc/passwd", "http://user:pass@192.168.1.1/", "http://192.168.1.1/a#fragment"))
            assertThrows(IOException.class, () -> UpnpProtocol.location("HTTP/1.1 200 OK\r\nLocation: " + uri + "\r\n\r\n", router));
        assertThrows(IOException.class, () -> UpnpProtocol.location("HTTP/1.1 200 OK\r\nLocation: http://8.8.8.8/", InetAddress.getByName("8.8.8.8")));
    }
    @Test void gatewayResolutionSupportsNamespacesAndValidatedUrlBase() throws Exception {
        var gateways = UpnpProtocol.gateways(DESCRIPTION, xml(document("", "/upnp/control")));
        assertEquals(URI.create("http://192.168.1.1:5000/upnp/control"), gateways.getFirst().control());
        assertEquals("urn:schemas-upnp-org:service:WANIPConnection:2", gateways.getFirst().service());
        var based = UpnpProtocol.gateways(DESCRIPTION, xml(document("<URLBase>http://192.168.1.1:5000/services/</URLBase>", "wan")));
        assertEquals(URI.create("http://192.168.1.1:5000/services/wan"), based.getFirst().control());
    }
    @Test void routerDescriptionCannotRedirectRequestsToAnotherHostOrPort() {
        assertThrows(IOException.class, () -> UpnpProtocol.gateways(DESCRIPTION, xml(document("", "http://8.8.8.8/control"))));
        assertThrows(IOException.class, () -> UpnpProtocol.gateways(DESCRIPTION, xml(document("", "http://192.168.1.1:9000/control"))));
        assertThrows(IOException.class, () -> UpnpProtocol.gateways(DESCRIPTION, xml(document("<URLBase>http://evil.example/</URLBase>", "/control"))));
    }
    @Test void externalEntitiesOversizedAndDeepXmlAreRejected() {
        assertThrows(IOException.class, () -> UpnpProtocol.value(xml("<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><root><value>&x;</value></root>"), "value"));
        assertThrows(IOException.class, () -> UpnpProtocol.value(new byte[65537], "value"));
        assertThrows(IOException.class, () -> UpnpProtocol.value(xml("<a>".repeat(80) + "x" + "</a>".repeat(80)), "a"));
    }
    @Test void soapEnvelopeEscapesDataAndValidatesAction() throws Exception {
        byte[] body = UpnpProtocol.request("urn:schemas-upnp-org:service:WANIPConnection:1", "AddPortMapping", Map.of("NewPortMappingDescription", "Party & <Friends>"));
        assertEquals("Party & <Friends>", UpnpProtocol.value(body, "NewPortMappingDescription"));
        assertThrows(IllegalArgumentException.class, () -> UpnpProtocol.request("invalid", "AddPortMapping", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> UpnpProtocol.request("urn:schemas-upnp-org:service:WANIPConnection:1", "<bad>", Map.of()));
    }
    @Test void duplicateOrMissingSoapValuesCannotSupplyAddress() {
        assertThrows(IOException.class, () -> UpnpProtocol.value(xml("<root/>"), "NewExternalIPAddress"));
        assertThrows(IOException.class, () -> UpnpProtocol.value(xml("<root><ip>1</ip><ip>2</ip></root>"), "ip"));
    }
    @Test void onlyOwnedEnabledFiniteMappingCanBeAdvertised() throws Exception {
        byte[] mapping = xml("<root><NewInternalClient>192.168.1.10</NewInternalClient><NewInternalPort>25565</NewInternalPort>"
                + "<NewPortMappingDescription>Party-owner-token</NewPortMappingDescription><NewEnabled>1</NewEnabled><NewLeaseDuration>1799</NewLeaseDuration></root>");
        assertTrue(UpnpProtocol.mappingOwned(mapping, "192.168.1.10", 25565, "Party-owner-token"));
        assertFalse(UpnpProtocol.mappingOwned(mapping, "192.168.1.11", 25565, "Party-owner-token"));
        assertFalse(UpnpProtocol.mappingOwned(mapping, "192.168.1.10", 25566, "Party-owner-token"));
        assertFalse(UpnpProtocol.mappingOwned(mapping, "192.168.1.10", 25565, "Another-party"));
        assertTrue(UpnpProtocol.temporaryMapping(mapping, 3600));
        assertFalse(UpnpProtocol.temporaryMapping(xml("<root><NewEnabled>1</NewEnabled><NewLeaseDuration>0</NewLeaseDuration></root>"), 3600));
        assertFalse(UpnpProtocol.temporaryMapping(xml("<root><NewEnabled>0</NewEnabled><NewLeaseDuration>1800</NewLeaseDuration></root>"), 3600));
    }
    @Test void publicAddressFilterRejectsPrivateCgnatAndSpecialRanges() {
        for (String address : List.of("192.168.1.10", "10.0.0.1", "172.16.1.1", "172.31.255.1", "100.64.0.1", "100.127.0.1", "127.0.0.1",
                "169.254.1.1", "224.1.1.1", "255.255.255.255", "192.0.2.1", "198.51.100.1", "203.0.113.1", "198.18.0.1", "0.1.2.3", "999.1.1.1", "example.com", "::1"))
            assertFalse(PublicAddress.ipv4(address), address);
        assertTrue(PublicAddress.ipv4("8.8.8.8")); assertTrue(PublicAddress.ipv4("1.1.1.1"));
        assertTrue(PublicAddress.ipv4("172.32.1.1")); assertTrue(PublicAddress.ipv4("100.128.1.1"));
    }
}
