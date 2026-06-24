package Email_backend.Email_backend.service;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * OneSignalService
 *
 * Handles all communication with the OneSignal Push Notification REST API (v1).
 *
 * How it works end-to-end:
 *  1. Flutter app initialises OneSignal SDK on device startup.
 *  2. After login, Flutter calls POST /api/notifications/register to save the
 *     device's OneSignal subscriptionId against the user's email address.
 *  3. Whenever the backend detects a relevant event (e.g. new email received),
 *     it calls sendPushToExternalId() which POSTs to OneSignal's REST API
 *     targeting that user's external_id.
 */
@Service
public class OneSignalService {

    private static final String ONESIGNAL_API_URL =
            "https://onesignal.com/api/v1/notifications";

    @Value("${onesignal.app-id}")
    private String appId;

    @Value("${onesignal.api-key}")
    private String apiKey;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sends a push notification to a specific user identified by their
     * external_id (typically their email address or user UUID set via
     * OneSignal.login() in the Flutter app).
     *
     * @param externalUserId The external ID used when calling OneSignal.login()
     *                       in the Flutter app.
     * @param title          Notification title text
     * @param message        Notification body text
     * @param emailSender    Optional: sender name appended to title for email alerts
     */
    public void sendPushToExternalId(
            String externalUserId,
            String title,
            String message,
            String emailSender
    ) {
        String displayTitle = (emailSender != null && !emailSender.trim().isEmpty())
                ? "📧 " + emailSender
                : title;

        String body = buildPayload(
                Collections.singletonList(externalUserId),
                "include_external_user_ids",
                displayTitle,
                message,
                null
        );

        postToOneSignal(body);
    }

    /**
     * Sends a push notification to ALL subscribed devices (broadcast).
     * Use this for system-wide announcements only.
     *
     * @param title   Notification title
     * @param message Notification body
     */
    public void sendPushToAll(String title, String message) {
        String body = buildBroadcastPayload(title, message);
        postToOneSignal(body);
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the JSON payload for targeting a list of external_ids or player_ids.
     *
     * @param targets   List of IDs to target
     * @param targetKey Either "include_external_user_ids" or "include_player_ids"
     * @param title     Notification title
     * @param message   Notification body
     * @param data      Optional JSON object string for additionalData (can be null)
     */
    private String buildPayload(
            List<String> targets,
            String targetKey,
            String title,
            String message,
            String data
    ) {
        StringBuilder ids = new StringBuilder("[");
        for (int i = 0; i < targets.size(); i++) {
            ids.append("\"").append(targets.get(i)).append("\"");
            if (i < targets.size() - 1) ids.append(",");
        }
        ids.append("]");

        String dataField = (data != null && !data.trim().isEmpty())
                ? ",\"data\":" + data
                : "";

        return "{"
                + "\"app_id\":\"" + appId + "\","
                + "\"" + targetKey + "\":" + ids + ","
                + "\"headings\":{\"en\":\"" + escapeJson(title) + "\"},"
                + "\"contents\":{\"en\":\"" + escapeJson(message) + "\"}"
                + dataField
                + "}";
    }

    /** Builds a broadcast payload targeting all subscribed users. */
    private String buildBroadcastPayload(String title, String message) {
        return "{"
                + "\"app_id\":\"" + appId + "\","
                + "\"included_segments\":[\"All\"],"
                + "\"headings\":{\"en\":\"" + escapeJson(title) + "\"},"
                + "\"contents\":{\"en\":\"" + escapeJson(message) + "\"}"
                + "}";
    }

    /** Executes the HTTP POST to the OneSignal API. */
    private void postToOneSignal(String jsonBody) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(ONESIGNAL_API_URL);
            request.setHeader("Content-Type", "application/json; charset=utf-8");
            request.setHeader("Authorization", "Basic " + apiKey);
            request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                if (statusCode >= 200 && statusCode < 300) {
                    System.out.println("[OneSignal] Push notification sent successfully. Status: " + statusCode);
                } else {
                    System.err.println("[OneSignal] Failed to send notification. HTTP Status: " + statusCode);
                }
            }
        } catch (IOException e) {
            System.err.println("[OneSignal] IOException while sending push notification: " + e.getMessage());
        }
    }

    /** Escapes special JSON characters in notification text. */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
