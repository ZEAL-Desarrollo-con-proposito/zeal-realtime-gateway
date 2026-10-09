package co.zeal.realtime.notification;

import co.zeal.realtime.shared.security.AuthenticatedUser;
import co.zeal.realtime.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Cada persona solo ve y cambia sus propias notificaciones. */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<Notification> list(@CurrentUser AuthenticatedUser user) {
        return notificationService.list(user.id());
    }

    @PostMapping("/{id}/leida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@CurrentUser AuthenticatedUser user, @PathVariable String id) {
        notificationService.markRead(user.id(), id);
    }

    @PostMapping("/leidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@CurrentUser AuthenticatedUser user) {
        notificationService.markAllRead(user.id());
    }
}
