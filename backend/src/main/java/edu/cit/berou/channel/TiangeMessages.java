package edu.cit.berou.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
final class TiangeMessages {

    private TiangeMessages() {
    }

    record HeartbeatRequest(String appName, String startedAt, long uptimeSeconds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HeartbeatResponse(String serverTime, int nextHeartbeatSeconds) {
    }

    record Listing(String sellerSku, String title, String supplierSku) {
    }

    record StockEntry(String sellerSku, int available) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedResponse(List<FeedEvent> events, long nextCursor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedEvent(long seq, String eventId, String type, String orderId,
                      String placedAt, String decisionDeadline, List<OrderLine> lines, Buyer buyer,
                      String cancelledAt, String confirmDeadline) {
    }

    record OrderLine(String sellerSku, int qty) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Buyer(String name, String city) {
    }

    record DecisionRequest(String decision, String shopOrderId, String reason) {
    }

    record ResolutionRequest(String status) {
    }

    record CancellationRequest(boolean restocked) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorBody(String error, String message) {
    }
}
