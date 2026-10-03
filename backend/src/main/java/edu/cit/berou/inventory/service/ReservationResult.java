package edu.cit.berou.inventory.service;

import edu.cit.berou.inventory.dto.InventorySnapshot;


public record ReservationResult(boolean success, String reason, InventorySnapshot inventory) {

    public static ReservationResult approved(InventorySnapshot inventory) {
        return new ReservationResult(true, null, inventory);
    }

    public static ReservationResult rejected(String reason, InventorySnapshot inventory) {
        return new ReservationResult(false, reason, inventory);
    }
}
