package edu.cit.berou.supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplierTranslatorTest {

    @Test
    void roundsUnitsUpToWholeCases() {
        assertEquals(3, SupplierTranslator.casesFor(30, 12)); 
        assertEquals(2, SupplierTranslator.casesFor(24, 12)); 
        assertEquals(1, SupplierTranslator.casesFor(1, 12));  
    }

    @Test
    void clampsToSupplierLimit() {
        assertEquals(99, SupplierTranslator.casesFor(100_000, 12));
    }

    @Test
    void rejectsNonPositiveInput() {
        assertThrows(IllegalArgumentException.class, () -> SupplierTranslator.casesFor(0, 12));
        assertThrows(IllegalArgumentException.class, () -> SupplierTranslator.casesFor(5, 0));
    }

    @Test
    void mapsKnownStatusCodesAndFlagsUnknownOnes() {
        assertEquals(SupplierOrderStatus.ACCEPTED, SupplierTranslator.toStatus(10).orElseThrow());
        assertEquals(SupplierOrderStatus.PICKING, SupplierTranslator.toStatus(20).orElseThrow());
        assertEquals(SupplierOrderStatus.SHIPPED, SupplierTranslator.toStatus(30).orElseThrow());
        assertEquals(SupplierOrderStatus.DELIVERED, SupplierTranslator.toStatus(40).orElseThrow());
        assertTrue(SupplierTranslator.toStatus(99).isEmpty());
        assertTrue(SupplierTranslator.toStatus(-1).isEmpty());
    }
}
