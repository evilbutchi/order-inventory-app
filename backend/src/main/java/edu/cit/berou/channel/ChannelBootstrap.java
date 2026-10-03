package edu.cit.berou.channel;

import edu.cit.berou.inventory.dto.InventorySnapshot;
import edu.cit.berou.inventory.service.InventoryService;
import edu.cit.berou.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Component
class ChannelBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ChannelBootstrap.class);

    private final TiangeClient tiangeClient;
    private final SupplierGateway supplierGateway;
    private final InventoryService inventoryService;
    private final ChannelProperties props;

    ChannelBootstrap(TiangeClient tiangeClient, SupplierGateway supplierGateway,
                     InventoryService inventoryService, ChannelProperties props) {
        this.tiangeClient = tiangeClient;
        this.supplierGateway = supplierGateway;
        this.inventoryService = inventoryService;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (props.clientId().isBlank() || props.apiKey().isBlank()) {
            log.error("LS_CLIENT_ID / LS_API_KEY are not set - Tiangge integration will not start.");
            return;
        }

        try {
            tiangeClient.heartbeat();
            log.info("Tiangge: first heartbeat sent.");
        } catch (ChannelException e) {
            log.error("Tiangge: first heartbeat failed ({}) - will keep retrying every {} ms.",
                    e.getMessage(), props.heartbeatIntervalMs());
        }

        List<TiangeMessages.Listing> listings = new ArrayList<>();
        List<TiangeMessages.StockEntry> stock = new ArrayList<>();
        for (Map.Entry<String, String> entry : props.listings()) {
            String productId = entry.getKey();
            String title = entry.getValue();
            var supplierSku = supplierGateway.supplierSkuFor(productId);
            if (supplierSku.isEmpty()) {
                log.warn("Skipping Tiangge listing for {}: no LegacySupply mapping yet (fill in sql/supplier.sql).", productId);
                continue;
            }
            listings.add(new TiangeMessages.Listing(productId, title, supplierSku.get()));
            try {
                InventorySnapshot snapshot = inventoryService.getItem(productId);
                stock.add(new TiangeMessages.StockEntry(productId, snapshot.stock()));
            } catch (RuntimeException e) {
                log.warn("Skipping initial stock for {}: {}", productId, e.getMessage());
            }
        }

        if (listings.isEmpty()) {
            log.error("No Tiangge listings to publish - check channel.listings and sql/supplier.sql.");
            return;
        }

        try {
            tiangeClient.putListings(listings);
            log.info("Tiangge: published {} listing(s).", listings.size());
        } catch (ChannelException e) {
            log.error("Tiangge: publishing listings failed: {}", e.getMessage());
            return; 
        }

        try {
            tiangeClient.putStock(stock);
            log.info("Tiangge: published initial stock for {} product(s).", stock.size());
        } catch (ChannelException e) {
            log.error("Tiangge: publishing initial stock failed: {} (next real stock change will self-correct it)", e.getMessage());
        }
    }
}
