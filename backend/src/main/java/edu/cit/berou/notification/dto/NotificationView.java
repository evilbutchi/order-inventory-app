package edu.cit.berou.notification.dto;

import java.time.Instant;

public record NotificationView(Long notificationId, String message, Instant createdAt) {
}
