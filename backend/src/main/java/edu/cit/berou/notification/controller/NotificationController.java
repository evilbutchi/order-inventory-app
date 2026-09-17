package edu.cit.berou.notification.controller;

import edu.cit.berou.notification.dto.NotificationView;
import edu.cit.berou.notification.repository.NotificationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/api/notifications")
    public List<NotificationView> listNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(n -> new NotificationView(n.getNotificationId(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}
