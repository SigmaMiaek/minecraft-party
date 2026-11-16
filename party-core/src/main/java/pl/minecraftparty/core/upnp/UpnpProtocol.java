package pl.minecraftparty.core.upnp;

import org.w3c.dom.*;
import org.xml.sax.InputSource;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;

/** Untrusted SSDP/XML parsing, separated from network I/O for deterministic verification. */
public final class UpnpProtocol {
    public static final int MAX_XML = 65536;
    private UpnpProtocol() { }
    public record Gateway(URI control, String service) { }
    public static URI location(String response, InetAddress responder) throws IOException {
        if (!responder.isSiteLocalAddress() || !(responder instanceof Inet4Address) || response.length() > 8192
                || !response.startsWith("HTTP/1.1 200")) throw new IOException("invalid discovery");
        for (String line : response.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).equalsIgnoreCase("location")) {
                try {
                    URI uri = URI.create(line.substring(colon + 1).strip());
                    validateUri(uri, responder.getHostAddress()); return uri;
                } catch (IllegalArgumentException e) { throw new IOException("invalid location", e); }
            }
        }
        throw new IOException("missing location");
    }
    public static List<Gateway> gateways(URI description, byte[] xml) throws IOException {
        Document doc = document(xml); List<Gateway> result = new ArrayList<>();
        URI base = description;
        NodeList bases = doc.getElementsByTagNameNS("*", "URLBase");
        if (bases.getLength() == 1 && !bases.item(0).getTextContent().isBlank()) {
            try { base = description.resolve(bases.item(0).getTextContent().strip()); }
            catch (IllegalArgumentException e) { throw new IOException("invalid URLBase", e); }
            validateUri(base, description.getHost());
            if (base.getPort() != description.getPort()) throw new IOException("gateway port changed");
        }
        NodeList services = doc.getElementsByTagNameNS("*", "service");
        for (int i = 0; i < Math.min(services.getLength(), 128); i++) {
            Element service = (Element) services.item(i);
            String type = child(service, "serviceType"), path = child(service, "controlURL");
            if (!type.matches("urn:schemas-upnp-org:service:(WANIPConnection:[12]|WANPPPConnection:1)")) continue;
            try {
                URI control = base.resolve(path);
                validateUri(control, description.getHost());
                if (control.getPort() != description.getPort()) continue;
                result.add(new Gateway(control, type));
            } catch (IllegalArgumentException | IOException ignored) { }
        }
        if (result.isEmpty()) throw new IOException("no gateway service");
        return List.copyOf(result);
    }
    public static String value(byte[] xml, String tag) throws IOException {
        NodeList nodes = document(xml).getElementsByTagNameNS("*", tag);
        if (nodes.getLength() != 1) throw new IOException("missing SOAP value");
        String value = nodes.item(0).getTextContent().strip();
        if (value.length() > 512) throw new IOException("SOAP value too long");
        return value;
    }
    public static boolean mappingOwned(byte[] xml, String client, int port, String description) throws IOException {
        return description.equals(value(xml, "NewPortMappingDescription")) && client.equals(value(xml, "NewInternalClient"))
                && Integer.toString(port).equals(value(xml, "NewInternalPort"));
    }
    public static boolean temporaryMapping(byte[] xml, int maxLease) throws IOException {
        String enabled = value(xml, "NewEnabled");
        try {
            int lease = Integer.parseInt(value(xml, "NewLeaseDuration"));
            return (enabled.equals("1") || enabled.equalsIgnoreCase("true")) && lease > 0 && lease <= maxLease;
        } catch (NumberFormatException e) { throw new IOException("invalid mapping lease", e); }
    }
    public static byte[] request(String service, String action, Map<String, String> arguments) {
        if (!service.matches("urn:schemas-upnp-org:service:(WANIPConnection:[12]|WANPPPConnection:1)")
                || !action.matches("[A-Za-z]{1,40}")) throw new IllegalArgumentException("SOAP action");
        StringBuilder body = new StringBuilder("<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:")
                .append(action).append(" xmlns:u=\"").append(service).append("\">");
        arguments.forEach((key, value) -> {
            if (!key.matches("[A-Za-z]{1,40}")) throw new IllegalArgumentException("SOAP argument");
            body.append('<').append(key).append('>').append(escape(value)).append("</").append(key).append('>');
        });
        body.append("</u:").append(action).append("><" + "/s:Body></s:Envelope>");
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static Document document(byte[] xml) throws IOException {
        if (xml.length > MAX_XML) throw new IOException("XML too large");
        try {
            var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", 64);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            return builder.parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (Exception e) { throw new IOException("invalid XML", e); }
    }
    private static String child(Element element, String tag) {
        for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element child && tag.equals(child.getLocalName())) return child.getTextContent().strip();
        return "";
    }
    private static void validateUri(URI uri, String host) throws IOException {
        if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getHost() == null || !uri.getHost().equals(host) || uri.getPort() == 0 || uri.getPort() > 65535
                || uri.toASCIIString().length() > 2048) throw new IOException("gateway URL must stay on responder");
    }
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
