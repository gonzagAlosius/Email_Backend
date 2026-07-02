package Email_backend.Email_backend.service;

import Email_backend.Email_backend.dto.EventRequest;
import Email_backend.Email_backend.model.Event;
import Email_backend.Email_backend.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@Service
public class EventService {

    @Autowired
    private EventRepository repo;

    @Autowired
    private IcsService icsService;

    @Autowired
    private MailService mailService;

    @Autowired
    private MicrosoftGraphService microsoftGraphService;

    public void createEvent(EventRequest req, String email, String password) {

        // 1. Save in DB
        Event event = new Event();
        event.setTitle(req.getTitle());
        event.setDescription(req.getDescription());
        event.setLocation(req.getLocation());
        event.setStartTime(req.getStartTime());
        event.setEndTime(req.getEndTime());
        event.setRecurrence(req.getRecurrence());
        event.setAllDay(req.isAllDay());
        event.setTeamsMeeting(req.isTeamsMeeting());
        event.setAgenda(req.getAgenda());
        event.setCategories(req.getCategories());
        event.setReminder(req.getReminder());
        event.setSensitivity(req.getSensitivity());
        event.setImportance(req.getImportance());
        event.setTimeZone(req.getTimeZone());
        event.setShowAs(req.getShowAs());
        event.setOrganizerEmail(email != null && !email.isEmpty() ? email : "admin@company.com");
        repo.save(event);

        // 2. Generate ICS
        String ics = icsService.generate(req);

        // 3. Send email to all attendees
        if (req.getAttendees() != null && email != null && password != null) {
            for(String attendeeEmail : req.getAttendees()) {
                try {
                    mailService.sendEventInvite(email, password, attendeeEmail, req.getTitle(), req.getDescription() == null ? "Calendar Event Invite" : req.getDescription(), ics);
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // 4. Sync to Microsoft Graph
        if (email != null && password != null) {
            String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : "";
            if (MailConfigDetector.isMicrosoftDomain(domain)) {
                String graphToken = MailConfigDetector.resolveGraphPassword(email, password);
                if (graphToken != null && MailConfigDetector.isOAuthToken(graphToken)) {
                    String graphId = microsoftGraphService.createCalendarEvent(graphToken, req);
                    if (graphId != null) {
                        event.setGraphEventId(graphId);
                        repo.save(event);
                    }
                }
            }
        }
    }

    public void updateEvent(Long id, EventRequest req, String email, String password) {
        Event event = repo.findById(id).orElseThrow(() -> new RuntimeException("Event not found"));
        event.setTitle(req.getTitle());
        event.setDescription(req.getDescription());
        event.setLocation(req.getLocation());
        event.setStartTime(req.getStartTime());
        event.setEndTime(req.getEndTime());
        event.setRecurrence(req.getRecurrence());
        event.setAllDay(req.isAllDay());
        event.setTeamsMeeting(req.isTeamsMeeting());
        event.setAgenda(req.getAgenda());
        event.setCategories(req.getCategories());
        event.setReminder(req.getReminder());
        event.setSensitivity(req.getSensitivity());
        event.setImportance(req.getImportance());
        event.setTimeZone(req.getTimeZone());
        event.setShowAs(req.getShowAs());
        repo.save(event);

        if (email != null && password != null && event.getGraphEventId() != null) {
            String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : "";
            if (MailConfigDetector.isMicrosoftDomain(domain)) {
                String graphToken = MailConfigDetector.resolveGraphPassword(email, password);
                if (graphToken != null && MailConfigDetector.isOAuthToken(graphToken)) {
                    microsoftGraphService.updateCalendarEvent(graphToken, event.getGraphEventId(), req);
                }
            }
        }
    }

    public void deleteEvent(Long id, String email, String password) {
        Event event = repo.findById(id).orElseThrow(() -> new RuntimeException("Event not found"));
        
        if (email != null && password != null && event.getGraphEventId() != null) {
            String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : "";
            if (MailConfigDetector.isMicrosoftDomain(domain)) {
                String graphToken = MailConfigDetector.resolveGraphPassword(email, password);
                if (graphToken != null && MailConfigDetector.isOAuthToken(graphToken)) {
                    microsoftGraphService.deleteCalendarEvent(graphToken, event.getGraphEventId());
                }
            }
        }
        
        repo.delete(event);
    }

    // GET ALL EVENTS
    public List<Event> getAllEvents(String email, String password) {
        List<Event> allEvents = new ArrayList<>();
        Map<String, Event> graphIdToLocalEvent = new java.util.HashMap<>();
        
        List<Event> localEvents = repo.findAll();
        for (Event e : localEvents) {
            // Filter local events to only include those where the current user is the organizer.
            // (If they are an attendee, MS Graph will fetch it for them anyway).
            if (email != null && e.getOrganizerEmail() != null && !e.getOrganizerEmail().equalsIgnoreCase(email)) {
                continue;
            }
            if (e.getGraphEventId() != null) {
                graphIdToLocalEvent.put(e.getGraphEventId(), e);
            }
            allEvents.add(e);
        }
        
        // 2. Fetch MS Graph events if valid credentials
        if (email != null && password != null) {
            System.out.println("[DEBUG] getAllEvents called with email: " + email);
            String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : "";
            if (MailConfigDetector.isMicrosoftDomain(domain)) {
                System.out.println("[DEBUG] It is a Microsoft domain. Resolving Graph token...");
                String graphToken = MailConfigDetector.resolveGraphPassword(email, password);
                if (graphToken != null && MailConfigDetector.isOAuthToken(graphToken)) {
                    System.out.println("[DEBUG] Successfully obtained Graph token.");
                    List<Event> graphEvents = microsoftGraphService.fetchCalendarEvents(graphToken);
                    System.out.println("[DEBUG] Fetched " + graphEvents.size() + " events from MS Graph.");
                    for (Event ge : graphEvents) {
                        if (ge.getGraphEventId() != null && graphIdToLocalEvent.containsKey(ge.getGraphEventId())) {
                            // Deduplicate: Event already exists locally, we keep the local one which has the DB ID.
                            continue;
                        }
                        allEvents.add(ge);
                    }
                } else {
                    System.out.println("[DEBUG] Failed to obtain Graph token or token is invalid.");
                }
            } else {
                System.out.println("[DEBUG] Not a Microsoft domain.");
            }
        } else {
            System.out.println("[DEBUG] Email or password is null. Email=" + email + ", Password=" + (password != null ? "***" : "null"));
        }
        
        return allEvents;
    }

    public Map<String, Object> getGraphEventById(String graphEventId, String email, String password) {
        if (email != null && password != null) {
            String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : "";
            if (MailConfigDetector.isMicrosoftDomain(domain)) {
                String graphToken = MailConfigDetector.resolveGraphPassword(email, password);
                if (graphToken != null && MailConfigDetector.isOAuthToken(graphToken)) {
                    return microsoftGraphService.fetchCalendarEventById(graphToken, graphEventId);
                }
            }
        }
        return null;
    }
}
