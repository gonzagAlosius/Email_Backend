package Email_backend.Email_backend.service;

import Email_backend.Email_backend.model.Event;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MicrosoftGraphService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public MicrosoftGraphService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public List<String> fetchAllUsers(String accessToken) {
        List<String> userEmails = new ArrayList<>();
        if (accessToken == null || accessToken.isEmpty()) {
            return userEmails;
        }

        String url = "https://graph.microsoft.com/v1.0/users?$select=mail,userPrincipalName,displayName&$top=100";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode valueNode = root.path("value");

                for (JsonNode userNode : valueNode) {
                    String mail = userNode.path("mail").asText(null);
                    if (mail == null || mail.isEmpty() || mail.equals("null")) {
                        mail = userNode.path("userPrincipalName").asText(null);
                    }
                    if (mail != null && !mail.isEmpty() && !mail.equals("null")) {
                        userEmails.add(mail);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching Microsoft Graph users: " + e.getMessage());
        }

        return userEmails;
    }

    public List<Event> fetchCalendarEvents(String accessToken) {
        List<Event> events = new ArrayList<>();
        if (accessToken == null || accessToken.isEmpty()) {
            return events;
        }

        String url = "https://graph.microsoft.com/v1.0/me/events?$select=id,subject,bodyPreview,start,end,location,organizer,attendees";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/json");
        // Prefer timezone as UTC for consistent parsing
        headers.set("Prefer", "outlook.timezone=\"UTC\"");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode valueNode = root.path("value");

                for (JsonNode eventNode : valueNode) {
                    Event event = new Event();
                    event.setTitle(eventNode.path("subject").asText(null));
                    event.setDescription(eventNode.path("bodyPreview").asText(null));

                    JsonNode locationNode = eventNode.path("location");
                    if (!locationNode.isMissingNode()) {
                        event.setLocation(locationNode.path("displayName").asText(null));
                    }

                    JsonNode startNode = eventNode.path("start");
                    if (!startNode.isMissingNode()) {
                        String startString = startNode.path("dateTime").asText(null);
                        if (startString != null) {
                            event.setStartTime(parseGraphDateTime(startString));
                        }
                    }

                    JsonNode endNode = eventNode.path("end");
                    if (!endNode.isMissingNode()) {
                        String endString = endNode.path("dateTime").asText(null);
                        if (endString != null) {
                            event.setEndTime(parseGraphDateTime(endString));
                        }
                    }
                    
                    JsonNode organizerNode = eventNode.path("organizer").path("emailAddress");
                    if (!organizerNode.isMissingNode()) {
                        event.setOrganizerEmail(organizerNode.path("address").asText(null));
                    }
                    
                    event.setGraphEventId(eventNode.path("id").asText(null));

                    events.add(event);
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching Microsoft Graph events: " + e.getMessage());
        }

        return events;
    }

    public Map<String, Object> fetchCalendarEventById(String accessToken, String graphEventId) {
        if (accessToken == null || accessToken.isEmpty() || graphEventId == null || graphEventId.isEmpty()) {
            return null;
        }

        String url = "https://graph.microsoft.com/v1.0/me/events/" + graphEventId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/json");
        headers.set("Prefer", "outlook.timezone=\"UTC\"");

        try {
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), Map.class);
            }
        } catch (Exception e) {
            System.err.println("Error fetching Microsoft Graph event by ID: " + e.getMessage());
        }
        return null;
    }

    private LocalDateTime parseGraphDateTime(String dateTimeString) {
        try {
            // Remove trailing 'Z' if present, Graph sometimes returns it with Z sometimes without depending on headers
            if (dateTimeString.endsWith("Z")) {
                dateTimeString = dateTimeString.substring(0, dateTimeString.length() - 1);
            }
            // Parse up to 7 decimal places for seconds. Often returns 2026-06-30T10:00:00.0000000
            if (dateTimeString.contains(".")) {
                int dotIndex = dateTimeString.indexOf(".");
                String fraction = dateTimeString.substring(dotIndex + 1);
                if (fraction.length() > 9) {
                     dateTimeString = dateTimeString.substring(0, dotIndex + 10);
                }
            }
            return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            System.err.println("Error parsing date: " + dateTimeString);
            return null;
        }
    }

    private Map<String, Object> buildGraphEventBody(Email_backend.Email_backend.dto.EventRequest req) {
        Map<String, Object> body = new HashMap<>();
        body.put("subject", req.getTitle() != null ? req.getTitle() : "Untitled Event");
        
        Map<String, String> bodyContent = new HashMap<>();
        bodyContent.put("contentType", "HTML");
        String content = "";
        if (req.getDescription() != null) content += req.getDescription() + "<br/><br/>";
        if (req.getAgenda() != null) content += "<b>Agenda:</b><br/>" + req.getAgenda();
        bodyContent.put("content", content);
        body.put("body", bodyContent);
        
        if (req.getStartTime() != null) {
            Map<String, String> start = new HashMap<>();
            start.put("dateTime", req.getStartTime().toString());
            start.put("timeZone", req.getTimeZone() != null ? req.getTimeZone() : "UTC");
            body.put("start", start);
        }
        
        if (req.getEndTime() != null) {
            Map<String, String> end = new HashMap<>();
            end.put("dateTime", req.getEndTime().toString());
            end.put("timeZone", req.getTimeZone() != null ? req.getTimeZone() : "UTC");
            body.put("end", end);
        }
        
        if (req.getLocation() != null && !req.getLocation().isEmpty()) {
            Map<String, String> location = new HashMap<>();
            location.put("displayName", req.getLocation());
            body.put("location", location);
        }
        
        List<Map<String, Object>> attendees = new ArrayList<>();
        if (req.getAttendees() != null) {
            for (String email : req.getAttendees()) {
                Map<String, Object> attendee = new HashMap<>();
                Map<String, String> emailAddress = new HashMap<>();
                emailAddress.put("address", email);
                attendee.put("emailAddress", emailAddress);
                attendee.put("type", "required");
                attendees.add(attendee);
            }
        }
        if (req.getOptionalAttendees() != null) {
            for (String email : req.getOptionalAttendees()) {
                Map<String, Object> attendee = new HashMap<>();
                Map<String, String> emailAddress = new HashMap<>();
                emailAddress.put("address", email);
                attendee.put("emailAddress", emailAddress);
                attendee.put("type", "optional");
                attendees.add(attendee);
            }
        }
        if (!attendees.isEmpty()) {
            body.put("attendees", attendees);
        }
        
        body.put("isAllDay", req.isAllDay());
        
        if (req.isTeamsMeeting()) {
            body.put("isOnlineMeeting", true);
            body.put("onlineMeetingProvider", "teamsForBusiness");
        }
        
        if (req.getImportance() != null && !req.getImportance().isEmpty()) {
            body.put("importance", req.getImportance().toLowerCase());
        }
        
        if (req.getSensitivity() != null && !req.getSensitivity().isEmpty()) {
            body.put("sensitivity", req.getSensitivity().toLowerCase());
        }
        return body;
    }

    public String createCalendarEvent(String accessToken, Email_backend.Email_backend.dto.EventRequest req) {
        if (accessToken == null || accessToken.isEmpty()) return null;
        
        String url = "https://graph.microsoft.com/v1.0/me/events";
        Map<String, Object> body = buildGraphEventBody(req);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Content-Type", "application/json");

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("onlineMeeting")) {
                JsonNode onlineMeeting = root.get("onlineMeeting");
                if (onlineMeeting.has("joinUrl")) {
                    req.setMeeturl(onlineMeeting.get("joinUrl").asText());
                }
            }
            if (root.has("id")) {
                return root.get("id").asText();
            }
        } catch (Exception e) {
            System.err.println("Error creating Microsoft Graph event: " + e.getMessage());
        }
        return null;
    }

    public void updateCalendarEvent(String accessToken, String graphEventId, Email_backend.Email_backend.dto.EventRequest req) {
        if (accessToken == null || accessToken.isEmpty() || graphEventId == null || graphEventId.isEmpty()) return;
        
        String url = "https://graph.microsoft.com/v1.0/me/events/" + graphEventId;
        Map<String, Object> body = buildGraphEventBody(req);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Content-Type", "application/json");
        // Use POST with method override in case underlying client doesn't support PATCH natively
        headers.set("X-HTTP-Method-Override", "PATCH");

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            System.err.println("Error updating Microsoft Graph event: " + e.getMessage());
        }
    }

    public void deleteCalendarEvent(String accessToken, String graphEventId) {
        if (accessToken == null || accessToken.isEmpty() || graphEventId == null || graphEventId.isEmpty()) return;
        
        String url = "https://graph.microsoft.com/v1.0/me/events/" + graphEventId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
        } catch (Exception e) {
            System.err.println("Error deleting Microsoft Graph event: " + e.getMessage());
        }
    }

    public void updateRsvpStatus(String accessToken, String graphEventId, String status) {
        if (accessToken == null || accessToken.isEmpty() || graphEventId == null || graphEventId.isEmpty()) return;
        
        String action = "tentativelyAccept";
        if ("ACCEPTED".equalsIgnoreCase(status)) {
            action = "accept";
        } else if ("DECLINED".equalsIgnoreCase(status)) {
            action = "decline";
        }

        String url = "https://graph.microsoft.com/v1.0/me/events/" + graphEventId + "/" + action;
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("sendResponse", true);

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            System.err.println("Error updating RSVP status on Microsoft Graph: " + e.getMessage());
            throw new RuntimeException("Error updating RSVP status on Microsoft Graph", e);
        }
    }
}
