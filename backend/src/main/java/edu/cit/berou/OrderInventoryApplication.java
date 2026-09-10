package edu.cit.berou;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Parent application class. Placed in edu.cit.berou so component scanning
 * picks up both edu.cit.berou.shop (Order module) and
 * edu.cit.berou.inventory (Inventory module) without either module needing
 * to know about the other's package.
 */
@SpringBootApplication
public class OrderInventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderInventoryApplication.class, args);
    }

}
