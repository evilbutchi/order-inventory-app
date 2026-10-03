package edu.cit.berou.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
class ChannelProperties {

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final String appName;
    private final long timeoutMs;
    private final int maxAttempts;
    private final long backoffBaseMs;
    private final long feedPollMs;
    private final int feedPageSize;
    private final long heartbeatIntervalMs;
    private final long flushIntervalMs;
    private final String listingsSpec;

    ChannelProperties(
            @Value("${channel.base-url:https://legacysupply.onrender.com/api/tiangge/v1}") String baseUrl,
            @Value("${channel.client-id:${LS_CLIENT_ID:}}") String clientId,
            @Value("${channel.api-key:${LS_API_KEY:}}") String apiKey,
            @Value("${spring.application.name:order-inventory-app}") String appName,
            @Value("${channel.timeout-ms:3000}") long timeoutMs,
            @Value("${channel.max-attempts:3}") int maxAttempts,
            @Value("${channel.backoff-base-ms:400}") long backoffBaseMs,
            @Value("${channel.feed-poll-ms:5000}") long feedPollMs,
            @Value("${channel.feed-page-size:50}") int feedPageSize,
            @Value("${channel.heartbeat-interval-ms:30000}") long heartbeatIntervalMs,
            @Value("${channel.flush-interval-ms:15000}") long flushIntervalMs,
            @Value("${channel.listings:P100:Wireless Mouse,P200:Mechanical Keyboard,P300:USB-C Hub}") String listingsSpec) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.appName = appName;
        this.timeoutMs = timeoutMs;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffBaseMs = backoffBaseMs;
        this.feedPollMs = feedPollMs;
        this.feedPageSize = feedPageSize;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.flushIntervalMs = flushIntervalMs;
        this.listingsSpec = listingsSpec;
    }

    String baseUrl() {
        return baseUrl;
    }

    String clientId() {
        return clientId;
    }

    String apiKey() {
        return apiKey;
    }

    String appName() {
        return appName;
    }

    long timeoutMs() {
        return timeoutMs;
    }

    int maxAttempts() {
        return maxAttempts;
    }

    long backoffBaseMs() {
        return backoffBaseMs;
    }

    long feedPollMs() {
        return feedPollMs;
    }

    int feedPageSize() {
        return feedPageSize;
    }

    long heartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    long flushIntervalMs() {
        return flushIntervalMs;
    }

    
    java.util.List<java.util.Map.Entry<String, String>> listings() {
        java.util.List<java.util.Map.Entry<String, String>> out = new java.util.ArrayList<>();
        for (String part : listingsSpec.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2 && !kv[0].isBlank()) {
                out.add(java.util.Map.entry(kv[0].trim(), kv[1].trim()));
            }
        }
        return out;
    }
}
