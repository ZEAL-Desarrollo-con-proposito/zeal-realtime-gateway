package co.zeal.realtime.presence;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estado inicial de presencia; los cambios llegan después por WebSocket. */
@RestController
@RequestMapping("/api/presencia")
public class PresenceController {

    private final PresenceRegistry registry;

    public PresenceController(PresenceRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/{userId}")
    public PresenceSnapshot presence(@PathVariable String userId) {
        return new PresenceSnapshot(userId, registry.statusOf(userId));
    }

    public record PresenceSnapshot(String userId, PresenceStatus status) {
    }
}
