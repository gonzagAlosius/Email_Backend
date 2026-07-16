package Email_backend.Email_backend.service;

import Email_backend.Email_backend.dto.EventRequest;
import Email_backend.Email_backend.model.Event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@Service
public class EventService {

    @Autowired
    private IcsService icsService;

    @Autowired
    private MailService mailService;

    @Autowired
    private MicrosoftGraphService microsoftGraphService;

    @Autowired
    private GoogleCalendarService googleCalendarService;

    @Autowired
    private BluehostCalendarService bluehostCalendarService;

    @Autowired
    private UnifiedCalendarService unifiedCalendarService;

    public void createEvent(EventRequest req, String email, String password) {
        req.setTeamsMeeting(true);

        // 1. Save in DB
        Event event = new Event();
        event.setTitle(req.getTitle());
        event.setDescription(req.getDescription());
        event.setLocation(req.getLocation());
        event.setStartTime(req.getStartTime());
        event.setEndTime(req.getEndTime());
        event.setRecurrence(req.getRecurrence());
        event.setAllDay(req.isAllDay());
        event.setTeamsMeeting(true);
        event.setAgenda(req.getAgenda());
        event.setCategories(req.getCategories());
        event.setReminder(req.getReminder());
        event.setSensitivity(req.getSensitivity());
        event.setImportance(req.getImportance());
        event.setTimeZone(req.getTimeZone());
        event.setShowAs(req.getShowAs());
        event.setOrganizerEmail(email != null && !email.isEmpty() ? email : "admin@company.com");
        event.setMeeturl(req.getMeeturl());
        // repo.save(event); // removed db save

        String ics = null;

        // 1b. Save in calender_dev.calendar002 and 003
        if (email != null && !email.isEmpty()) {
            try {
                Integer calid = req.getCalid();
                Integer orgcode = req.getOrgcode();
                
                if (calid == null || orgcode == null) {
                    java.util.List<Email_backend.Email_backend.model.Calendar001> userCalendars = unifiedCalendarService.getCalendarsByUserId(email);
                    if (userCalendars != null && !userCalendars.isEmpty()) {
                        calid = userCalendars.get(0).getCalid();
                        orgcode = userCalendars.get(0).getOrgcode();
                    } else {
                        Email_backend.Email_backend.model.Calendar001 newCal = new Email_backend.Email_backend.model.Calendar001();
                        newCal.setUserid(email);
                        newCal.setCalname("Default Calendar");
                        newCal.setTimezone("UTC");
                        newCal.setCountry("US");
                        newCal.setOrgcode(1);
                        Email_backend.Email_backend.model.Calendar001 savedCal = unifiedCalendarService.createCalendar(newCal);
                        if (savedCal != null && savedCal.getCalid() != null) {
                            calid = savedCal.getCalid();
                            orgcode = savedCal.getOrgcode();
                        } else {
                            calid = 1;
                            orgcode = 1;
                        }
                    }
                }

                Email_backend.Email_backend.model.Calendar002 cal2 = new Email_backend.Email_backend.model.Calendar002();
                cal2.setCalid(calid);
                cal2.setOrgcode(orgcode);
                cal2.setTitle(req.getTitle());
                cal2.setDescription(req.getDescription());
                cal2.setLocation(req.getLocation());
                cal2.setStartTime(req.getStartTime());
                cal2.setEndTime(req.getEndTime());
                cal2.setIsAllDay(req.isAllDay() ? 1 : 0);
                cal2.setRecurrenceRule(req.getRecurrence());
                cal2.setStatus("CONFIRMED");
                cal2.setMeeturl(req.getMeeturl());

                java.util.List<Email_backend.Email_backend.model.Calendar003> attendees = new java.util.ArrayList<>();
                if (req.getAttendees() != null) {
                    for (String att : req.getAttendees()) {
                        Email_backend.Email_backend.model.Calendar003 cal3 = new Email_backend.Email_backend.model.Calendar003();
                        cal3.setEmail(att);
                        cal3.setDisplayName(att);
                        cal3.setIsOptional(false);
                        cal3.setResponseStatus("NEEDS-ACTION");
                        attendees.add(cal3);
                    }
                }
                
                if (req.getOptionalAttendees() != null) {
                    for (String att : req.getOptionalAttendees()) {
                        Email_backend.Email_backend.model.Calendar003 cal3 = new Email_backend.Email_backend.model.Calendar003();
                        cal3.setEmail(att);
                        cal3.setDisplayName(att);
                        cal3.setIsOptional(true);
                        cal3.setResponseStatus("NEEDS-ACTION");
                        attendees.add(cal3);
                    }
                }

                UnifiedCalendarService.EventCreationRequest uReq = new UnifiedCalendarService.EventCreationRequest();
                uReq.setEvent(cal2);
                uReq.setAttendees(attendees);

                Email_backend.Email_backend.model.Calendar002 savedEvent = unifiedCalendarService.createEvent(email, uReq);
                Integer savedEventId = savedEvent != null ? savedEvent.getEventid() : null;
                System.out.println("[DEBUG] Successfully inserted event into calendar002 and 003 for user: " + email + ", eventId: " + savedEventId);
                
                // Generate ICS with the correct IDs
                ics = icsService.generate(req, email, orgcode, calid, savedEventId);
                
            } catch (Exception e) {
                System.err.println("[ERROR] Failed to insert into calendar002/003: " + e.getMessage());
                e.printStackTrace();
                try {
                    java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(System.getProperty("user.home") + "/Desktop/calendar_error.txt", true));
                    pw.println("Error inserting into calendar002/003: " + e.getMessage());
                    e.printStackTrace(pw);
                    pw.close();
                } catch (Exception ex) {}
            }
        }

        // 3. Send email to all attendees
        if (req.getAttendees() != null && email != null) {
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
                        // repo.save(event);
                    }
                }
            } else if (MailConfigDetector.isGoogleDomain(domain)) {
                if (password != null && MailConfigDetector.isOAuthToken(password)) {
                    String googleId = googleCalendarService.createCalendarEvent(password, req);
                    if (googleId != null) {
                        event.setGraphEventId(googleId);
                        // repo.save(event);
                    }
                }
            } else {
                String bhId = bluehostCalendarService.createCalendarEvent(email, password, req, null);
                if (bhId != null) {
                    event.setGraphEventId(bhId);
                    // repo.save(event);
                }
            }
        }
    }

    public void updateEvent(Long id, EventRequest req, String email, String password) {
        // We no longer have local events fetched by Long id in repo.
        // We only support updating external integrations via ID fallback or relying on unifiedCalendarService
        // This is a simplified fallback since we removed repo.
        String graphEventId = id.toString(); // Just a stub for graph since repo is gone

        if (email != null && password != null && graphEventId != null) {
            String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : "";
            if (MailConfigDetector.isMicrosoftDomain(domain)) {
                String graphToken = MailConfigDetector.resolveGraphPassword(email, password);
                if (graphToken != null && MailConfigDetector.isOAuthToken(graphToken)) {
                    microsoftGraphService.updateCalendarEvent(graphToken, graphEventId, req);
                }
            } else if (MailConfigDetector.isGoogleDomain(domain)) {
                if (password != null && MailConfigDetector.isOAuthToken(password)) {
                    googleCalendarService.updateCalendarEvent(password, graphEventId, req);
                }
            } else {
                String existingUuid = graphEventId.startsWith("bluehost_") ? graphEventId.substring(9) : graphEventId;
                bluehostCalendarService.createCalendarEvent(email, password, req, existingUuid);
            }
        }
    }

    public void deleteEvent(Long id, String email, String password) {
        // We no longer have local events fetched by Long id in repo.
        // Fallback for Graph API integration using id as string representation of graphEventId
        // This is a simplified fallback since we removed repo.
        String graphEventId = id.toString(); // Just a stub for graph since repo is gone
        
        if (email != null && password != null && graphEventId != null) {
            String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : "";
            if (MailConfigDetector.isMicrosoftDomain(domain)) {
                String graphToken = MailConfigDetector.resolveGraphPassword(email, password);
                if (graphToken != null && MailConfigDetector.isOAuthToken(graphToken)) {
                    microsoftGraphService.deleteCalendarEvent(graphToken, graphEventId);
                }
            } else if (MailConfigDetector.isGoogleDomain(domain)) {
                if (password != null && MailConfigDetector.isOAuthToken(password)) {
                    googleCalendarService.deleteCalendarEvent(password, graphEventId);
                }
            } else {
                bluehostCalendarService.deleteCalendarEvent(email, password, graphEventId);
            }
        }
    }

    // GET ALL EVENTS
    public List<Event> getAllEvents(String email, String password, Integer calid, Integer orgcode) {
        List<Event> allEvents = new ArrayList<>();
        
        if (calid != null && orgcode != null) {
            List<Email_backend.Email_backend.model.Calendar002> cal2Events = unifiedCalendarService.getEventsByCalendar(orgcode, calid);
            if (cal2Events != null) {
                for (Email_backend.Email_backend.model.Calendar002 c2 : cal2Events) {
                    Event e = new Event();
                    e.setId(c2.getEventid().longValue());
                    e.setTitle(c2.getTitle());
                    e.setDescription(c2.getDescription());
                    e.setLocation(c2.getLocation());
                    e.setStartTime(c2.getStartTime());
                    e.setEndTime(c2.getEndTime());
                    e.setAllDay(c2.getIsAllDay() != null && c2.getIsAllDay() == 1);
                    e.setRecurrence(c2.getRecurrenceRule());
                    e.setOrganizerEmail(email);
                    e.setCalid(c2.getCalid());
                    e.setOrgcode(c2.getOrgcode());
                    e.setMeeturl(c2.getMeeturl());
                    e.setStatus(c2.getStatus());
                    allEvents.add(e);
                }
            }
            return allEvents;
        }

        Map<String, Event> graphIdToLocalEvent = new java.util.HashMap<>();
        
        // 1. We no longer fetch local events from repo
        // allEvents from email_dev.events are ignored.
        
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
            } else if (MailConfigDetector.isGoogleDomain(domain)) {
                System.out.println("[DEBUG] It is a Google domain. Fetching events...");
                if (password != null && MailConfigDetector.isOAuthToken(password)) {
                    List<Event> googleEvents = googleCalendarService.fetchCalendarEvents(password);
                    System.out.println("[DEBUG] Fetched " + googleEvents.size() + " events from Google Calendar.");
                    for (Event ge : googleEvents) {
                        if (ge.getGraphEventId() != null && graphIdToLocalEvent.containsKey(ge.getGraphEventId())) {
                            continue;
                        }
                        allEvents.add(ge);
                    }
                } else {
                    System.out.println("[DEBUG] Failed to obtain Google token or token is invalid.");
                }
            } else {
                System.out.println("[DEBUG] Not a Microsoft or Google domain. Trying Bluehost CalDAV...");
                List<Event> bluehostEvents = bluehostCalendarService.fetchCalendarEvents(email, password);
                System.out.println("[DEBUG] Fetched " + bluehostEvents.size() + " events from Bluehost CalDAV.");
                for (Event ge : bluehostEvents) {
                    if (ge.getGraphEventId() != null && graphIdToLocalEvent.containsKey(ge.getGraphEventId())) {
                        continue;
                    }
                    allEvents.add(ge);
                }
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
            } else if (MailConfigDetector.isGoogleDomain(domain)) {
                if (password != null && MailConfigDetector.isOAuthToken(password)) {
                    return googleCalendarService.fetchCalendarEventById(password, graphEventId);
                }
            }
        }
        return null;
    }

    public void updateExternalRsvp(String graphEventId, String email, String password, String status) {
        if (email == null || email.isEmpty() || password == null || password.isEmpty() || graphEventId == null) {
            return;
        }
        String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : "";
        if (MailConfigDetector.isMicrosoftDomain(domain)) {
            String graphToken = MailConfigDetector.resolveGraphPassword(email, password);
            if (graphToken != null && MailConfigDetector.isOAuthToken(graphToken)) {
                microsoftGraphService.updateRsvpStatus(graphToken, graphEventId, status);
            }
        } else if (MailConfigDetector.isGoogleDomain(domain)) {
            if (password != null && MailConfigDetector.isOAuthToken(password)) {
                googleCalendarService.updateRsvpStatus(password, graphEventId, email, status);
            }
        }
    }
}
