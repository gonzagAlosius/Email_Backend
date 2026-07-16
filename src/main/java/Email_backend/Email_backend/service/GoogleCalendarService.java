package Email_backend.Email_backend.service;

import Email_backend.Email_backend.model.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GoogleCalendarService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    public GoogleCalendarService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    private String getAccessToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        
        // If it starts with ya29., it's already an access token
        if (token.startsWith("ya29.")) {
            return token;
        }

        // Otherwise, assume it's a refresh token and exchange it
        try {
            String tokenEndpoint = "https://oauth2.googleapis.com/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String requestBody = "client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                    "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8") +
                    "&refresh_token=" + URLEncoder.encode(token, "UTF-8") +
                    "&grant_type=refresh_token";

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenEndpoint, entity, Map.class);

            if (tokenResponse.getStatusCode() == HttpStatus.OK && tokenResponse.getBody() != null) {
                return (String) tokenResponse.getBody().get("access_token");
            }
        } catch (Exception e) {
            System.err.println("Error exchanging Google refresh token: " + e.getMessage());
        }
        return null; // Fallback or failed
    }

    public List<Event> fetchCalendarEvents(String token) {
        List<Event> events = new ArrayList<>();
        String accessToken = getAccessToken(token);
        if (accessToken == null) return events;

        String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?singleEvents=true&orderBy=startTime";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode itemsNode = root.path("items");

                for (JsonNode eventNode : itemsNode) {
                    Event event = new Event();
                    event.setTitle(eventNode.path("summary").asText(null));
                    event.setDescription(eventNode.path("description").asText(null));
                    event.setLocation(eventNode.path("location").asText(null));
                    
                    JsonNode startNode = eventNode.path("start");
                    if (!startNode.isMissingNode()) {
                        String startString = startNode.path("dateTime").asText(null);
                        if (startString == null) startString = startNode.path("date").asText(null); // All day event
                        event.setStartTime(parseGoogleDateTime(startString));
                    }

                    JsonNode endNode = eventNode.path("end");
                    if (!endNode.isMissingNode()) {
                        String endString = endNode.path("dateTime").asText(null);
                        if (endString == null) endString = endNode.path("date").asText(null);
                        event.setEndTime(parseGoogleDateTime(endString));
                    }
                    
                    JsonNode organizerNode = eventNode.path("organizer");
                    if (!organizerNode.isMissingNode()) {
                        event.setOrganizerEmail(organizerNode.path("email").asText(null));
                    }
                    
                    event.setGraphEventId(eventNode.path("id").asText(null));
                    events.add(event);
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching Google Calendar events: " + e.getMessage());
        }
        return events;
    }

    public Map<String, Object> fetchCalendarEventById(String token, String eventId) {
        String accessToken = getAccessToken(token);
        if (accessToken == null || eventId == null || eventId.isEmpty()) return null;

        String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events/" + eventId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/json");

        try {
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), Map.class);
            }
        } catch (Exception e) {
            System.err.println("Error fetching Google Calendar event by ID: " + e.getMessage());
        }
        return null;
    }

    private LocalDateTime parseGoogleDateTime(String dateTimeString) {
        if (dateTimeString == null) return null;
        try {
            // If it's just a date (YYYY-MM-DD), append time
            if (dateTimeString.length() == 10) {
                dateTimeString += "T00:00:00Z";
            }
            if (dateTimeString.endsWith("Z")) {
                dateTimeString = dateTimeString.substring(0, dateTimeString.length() - 1);
                return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } else if (dateTimeString.contains("+") || dateTimeString.contains("-")) {
                return OffsetDateTime.parse(dateTimeString, DateTimeFormatter.ISO_OFFSET_DATE_TIME).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            }
            return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            System.err.println("Error parsing Google date: " + dateTimeString);
            return null;
        }
    }

    private Map<String, Object> buildGoogleEventBody(Email_backend.Email_backend.dto.EventRequest req) {
        Map<String, Object> body = new HashMap<>();
        body.put("summary", req.getTitle() != null ? req.getTitle() : "Untitled Event");
        
        String content = "";
        if (req.getDescription() != null) content += req.getDescription() + "\n\n";
        if (req.getAgenda() != null) content += "Agenda:\n" + req.getAgenda();
        if (!content.isEmpty()) {
            body.put("description", content);
        }
        
        if (req.getStartTime() != null) {
            Map<String, String> start = new HashMap<>();
            start.put("dateTime", req.getStartTime().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            start.put("timeZone", "UTC");
            body.put("start", start);
        }
        
        if (req.getEndTime() != null) {
            Map<String, String> end = new HashMap<>();
            end.put("dateTime", req.getEndTime().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            end.put("timeZone", "UTC");
            body.put("end", end);
        }
        
        if (req.getLocation() != null && !req.getLocation().isEmpty()) {
            body.put("location", req.getLocation());
        }
        
        List<Map<String, Object>> attendees = new ArrayList<>();
        if (req.getAttendees() != null) {
            for (String email : req.getAttendees()) {
                Map<String, Object> attendee = new HashMap<>();
                attendee.put("email", email);
                attendee.put("optional", false);
                attendees.add(attendee);
            }
        }
        if (req.getOptionalAttendees() != null) {
            for (String email : req.getOptionalAttendees()) {
                Map<String, Object> attendee = new HashMap<>();
                attendee.put("email", email);
                attendee.put("optional", true);
                attendees.add(attendee);
            }
        }
        if (!attendees.isEmpty()) {
            body.put("attendees", attendees);
            
            // Generate a meet link for meetings
            Map<String, Object> conferenceData = new HashMap<>();
            Map<String, Object> createRequest = new HashMap<>();
            createRequest.put("requestId", java.util.UUID.randomUUID().toString());
            Map<String, String> conferenceSolutionKey = new HashMap<>();
            conferenceSolutionKey.put("type", "hangoutsMeet");
            createRequest.put("conferenceSolutionKey", conferenceSolutionKey);
            conferenceData.put("createRequest", createRequest);
            body.put("conferenceData", conferenceData);
        }
        return body;
    }

    public String createCalendarEvent(String token, Email_backend.Email_backend.dto.EventRequest req) {
        String accessToken = getAccessToken(token);
        if (accessToken == null) return null;
        
        String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?sendUpdates=all&conferenceDataVersion=1";
        Map<String, Object> body = buildGoogleEventBody(req);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Content-Type", "application/json");

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            
            if (root.has("hangoutLink")) {
                req.setMeeturl(root.get("hangoutLink").asText());
            }

            if (root.has("id")) {
                return root.get("id").asText();
            }
        } catch (Exception e) {
            System.err.println("Error creating Google Calendar event: " + e.getMessage());
        }
        return null;
    }

    public void updateCalendarEvent(String token, String eventId, Email_backend.Email_backend.dto.EventRequest req) {
        String accessToken = getAccessToken(token);
        if (accessToken == null || eventId == null || eventId.isEmpty()) return;
        
        String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events/" + eventId + "?sendUpdates=all&conferenceDataVersion=1";
        Map<String, Object> body = buildGoogleEventBody(req);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Content-Type", "application/json");

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        } catch (Exception e) {
            System.err.println("Error updating Google Calendar event: " + e.getMessage());
        }
    }

    public void deleteCalendarEvent(String token, String eventId) {
        String accessToken = getAccessToken(token);
        if (accessToken == null || eventId == null || eventId.isEmpty()) return;
        
        String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events/" + eventId + "?sendUpdates=all";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
        } catch (Exception e) {
            System.err.println("Error deleting Google Calendar event: " + e.getMessage());
        }
    }

    public void updateRsvpStatus(String token, String eventId, String currentUserEmail, String status) {
        String accessToken = getAccessToken(token);
        if (accessToken == null || eventId == null || eventId.isEmpty() || currentUserEmail == null) return;

        Map<String, Object> event = fetchCalendarEventById(token, eventId);
        if (event == null) return;

        String googleStatus = "tentative";
        if ("ACCEPTED".equalsIgnoreCase(status)) {
            googleStatus = "accepted";
        } else if ("DECLINED".equalsIgnoreCase(status)) {
            googleStatus = "declined";
        }

        boolean updated = false;
        if (event.containsKey("attendees")) {
            List<Map<String, Object>> attendees = (List<Map<String, Object>>) event.get("attendees");
            for (Map<String, Object> attendee : attendees) {
                String email = (String) attendee.get("email");
                if (currentUserEmail.equalsIgnoreCase(email)) {
                    attendee.put("responseStatus", googleStatus);
                    updated = true;
                    break;
                }
            }
        }

        if (updated) {
            String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events/" + eventId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Type", "application/json");

            try {
                String jsonBody = objectMapper.writeValueAsString(event);
                HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
                restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            } catch (Exception e) {
                System.err.println("Error updating RSVP status on Google Calendar: " + e.getMessage());
                throw new RuntimeException("Error updating RSVP status on Google Calendar", e);
            }
        }
    }
}
