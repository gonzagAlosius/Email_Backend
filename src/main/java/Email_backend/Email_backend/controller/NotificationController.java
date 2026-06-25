package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.model.UserEmailConfig;
import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import Email_backend.Email_backend.service.OneSignalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * NotificationController
 *
 * Exposes REST endpoints for device push notification management.
 *
 * Endpoints:
 *  POST /api/notifications/register   - Saves a device's OneSignal subscription ID
 *                                       against the user's email so the backend can
 *                                       send targeted pushes later.
 *  POST /api/notifications/test       - Sends a test push to a specific user (dev use).
 */
@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    @Autowired
    private OneSignalService oneSignalService;

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    // -------------------------------------------------------------------------
    // POST /api/notifications/register
    // -------------------------------------------------------------------------
    /**
     * Called by the Flutter app right after the user logs in.
     * Saves the OneSignal subscription ID to the user's record so the backend
     * knows which device to target when sending a notification.
     *
     * Request body:
     * {
     *   "email": "user@example.com",
     *   "subscriptionId": "onesignal-subscription-uuid"
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerDevice(@RequestBody Map<String, String> body) {
        String email          = body.get("email");
        String subscriptionId = body.get("subscriptionId");

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Missing 'email' field.");
        }
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Missing 'subscriptionId' field.");
        }

        Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
        if (!configOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No user configuration found for email: " + email);
        }

        UserEmailConfig config = configOpt.get();
        config.setOneSignalSubscriptionId(subscriptionId);
        userEmailConfigRepository.save(config);

        System.out.println("[Notification] Registered subscriptionId for " + email + ": " + subscriptionId);
        return ResponseEntity.ok(Collections.singletonMap("message", "Device registered successfully."));
    }

    // -------------------------------------------------------------------------
    // POST /api/notifications/test
    // -------------------------------------------------------------------------
    /**
     * Sends a test push notification to a specific user by their email address.
     * Useful for testing the end-to-end push pipeline in development.
     *
     * Request body:
     * {
     *   "email": "user@example.com",
     *   "title": "Test Title",
     *   "message": "Hello from backend!"
     * }
     */
    @PostMapping("/test")
    public ResponseEntity<?> sendTestNotification(@RequestBody Map<String, String> body) {
        String email   = body.get("email");
        String title   = body.getOrDefault("title", "📬 Test Notification");
        String message = body.getOrDefault("message", "OneSignal is connected and working!");

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Missing 'email' field.");
        }

        // Use the email address as the external_id (matches what Flutter sets via
        // OneSignal.login(email) in notification_service.dart)
        oneSignalService.sendPushToExternalId(email, title, message, null);

        return ResponseEntity.ok(Collections.singletonMap(
                "message", "Test notification dispatched for: " + email
        ));
    }
}
