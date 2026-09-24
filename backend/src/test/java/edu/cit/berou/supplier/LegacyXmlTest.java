package edu.cit.berou.supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyXmlTest {

    @Test
    void parsesAcknowledgement() {
        LegacyXml.PoDoc po = LegacyXml.parsePurchaseOrder(
                "<PurchaseOrderAck><PoNumber>PO-100231</PoNumber><StatusCode>10</StatusCode>"
                        + "<SupplierSku>ABC-1234</SupplierSku><Qty>2</Qty><Uom>CS</Uom>"
                        + "<BuyerRef>RO-7</BuyerRef></PurchaseOrderAck>");
        assertEquals("PO-100231", po.poNumber());
        assertEquals(10, po.statusCode());
        assertEquals(2, po.qty());
        assertEquals("CS", po.uom());
        assertEquals("RO-7", po.buyerRef());
    }

    @Test
    void parsesErrorsAndNeverThrowsOnGarbage() {
        LegacyXml.ErrDoc e = LegacyXml.parseError("<LSError><Code>E-QTY-11</Code><Message>Quantity invalid.</Message></LSError>");
        assertEquals("E-QTY-11", e.code());
        assertEquals("UNKNOWN", LegacyXml.parseError("<html>bad gateway").code());
        assertEquals("UNKNOWN", LegacyXml.parseError(null).code());
    }

    @Test
    void malformedSuccessBodyIsTreatedAsTransient() {
        LegacySupplyException ex = assertThrows(LegacySupplyException.class,
                () -> LegacyXml.parsePurchaseOrder("not xml"));
        assertEquals(LegacySupplyException.Kind.TRANSIENT, ex.kind());
    }

    @Test
    void escapesValuesInRequests() {
        assertEquals("<PurchaseOrder><SupplierSku>A&amp;B</SupplierSku><Qty>3</Qty><BuyerRef>RO-1</BuyerRef></PurchaseOrder>",
                LegacyXml.purchaseOrder("A&B", 3, "RO-1"));
    }
}
