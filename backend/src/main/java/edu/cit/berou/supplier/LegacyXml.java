package edu.cit.berou.supplier;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;

/**
 * All XML for LegacySupply lives here: how we build requests and how we read
 * responses. Plain JDK DOM parsing - no extra dependency, and (unlike adding
 * jackson-dataformat-xml) it cannot change how the rest of the app's REST
 * endpoints negotiate JSON vs XML.
 */
final class LegacyXml {

    /** A PurchaseOrderAck / PurchaseOrderStatus document. statusCode is -1 if not a number. */
    record PoDoc(String poNumber, int statusCode, String sku, int qty, String uom, String buyerRef) {
    }

    /** An LSError document. */
    record ErrDoc(String code, String message) {
    }

    private LegacyXml() {
    }

    // ---- requests -------------------------------------------------------

    static String authRequest(String clientId, String apiKey) {
        return "<AuthRequest><ClientId>" + esc(clientId) + "</ClientId><ApiKey>" + esc(apiKey)
                + "</ApiKey></AuthRequest>";
    }

    static String purchaseOrder(String sku, int qty, String buyerRef) {
        return "<PurchaseOrder><SupplierSku>" + esc(sku) + "</SupplierSku><Qty>" + qty
                + "</Qty><BuyerRef>" + esc(buyerRef) + "</BuyerRef></PurchaseOrder>";
    }

    // ---- responses ------------------------------------------------------

    static String parseSessionToken(String xml) {
        String token = text(root(xml), "SessionToken");
        if (token == null || token.isBlank()) {
            throw LegacySupplyException.transientFailure("Sign-in response had no SessionToken", null);
        }
        return token;
    }

    static PoDoc parsePurchaseOrder(String xml) {
        Element r = root(xml);
        String po = text(r, "PoNumber");
        if (po == null || po.isBlank()) {
            throw LegacySupplyException.transientFailure("Purchase order response had no PoNumber", null);
        }
        return new PoDoc(po,
                toInt(text(r, "StatusCode"), -1),
                text(r, "SupplierSku"),
                toInt(text(r, "Qty"), 0),
                text(r, "Uom"),
                text(r, "BuyerRef"));
    }

    /** Never throws: an unreadable error body just becomes code "UNKNOWN". */
    static ErrDoc parseError(String xml) {
        try {
            Element r = root(xml);
            String code = text(r, "Code");
            String msg = text(r, "Message");
            return new ErrDoc(code == null ? "UNKNOWN" : code, msg == null ? "" : msg);
        } catch (RuntimeException e) {
            return new ErrDoc("UNKNOWN", "");
        }
    }

    // ---- helpers --------------------------------------------------------

    private static Element root(String xml) {
        if (xml == null || xml.isBlank()) {
            throw LegacySupplyException.transientFailure("Empty XML body from LegacySupply", null);
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = f.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler()); // quiet: we report problems ourselves
            return builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw LegacySupplyException.transientFailure("Malformed XML from LegacySupply", e);
        }
    }

    private static String text(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private static int toInt(String s, int fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
