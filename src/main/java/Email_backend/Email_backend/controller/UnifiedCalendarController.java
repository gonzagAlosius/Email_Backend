package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.model.Calendar001;
import Email_backend.Email_backend.model.Calendar002;
import Email_backend.Email_backend.model.Calendar003;
import Email_backend.Email_backend.service.UnifiedCalendarService;
import Email_backend.Email_backend.service.UnifiedCalendarService.EventCreationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*")
public class UnifiedCalendarController {

    @Autowired
    private UnifiedCalendarService unifiedCalendarService;

    // ==========================================
    // Calendar001 Endpoints (formerly in /api/calendars)
    // ==========================================

    @PostMapping("/api/calendars")
    public ResponseEntity<?> createCalendar(
            @RequestBody Calendar001 calendar,
            @RequestHeader(value = "X-Email", required = false) String emailHeader) {
        try {
            if (emailHeader != null && !emailHeader.isEmpty() && (calendar.getUserid() == null || calendar.getUserid().isEmpty())) {
                calendar.setUserid(emailHeader);
            }
            Calendar001 createdCalendar = unifiedCalendarService.createCalendar(calendar);
            return new ResponseEntity<>(createdCalendar, HttpStatus.CREATED);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/api/calendars")
    public ResponseEntity<List<Calendar001>> getAllCalendars(@RequestHeader(value = "X-Email", required = false) String email) {
        try {
            List<Calendar001> calendars = unifiedCalendarService.getCalendarsForUser(email);
            if (calendars.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(calendars, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/api/calendars/{id}")
    public ResponseEntity<Calendar001> getCalendarById(@PathVariable Integer id) {
        Optional<Calendar001> calendar = unifiedCalendarService.getCalendarById(id);
        return calendar.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/api/calendars/user/{userid}")
    public ResponseEntity<List<Calendar001>> getCalendarsByUserId(@PathVariable String userid) {
        try {
            List<Calendar001> calendars = unifiedCalendarService.getCalendarsByUserId(userid);
            if (calendars.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(calendars, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/api/calendars/{id}")
    public ResponseEntity<Calendar001> updateCalendar(@PathVariable Integer id, @RequestBody Calendar001 calendar) {
        try {
            Calendar001 updatedCalendar = unifiedCalendarService.updateCalendar(id, calendar);
            return new ResponseEntity<>(updatedCalendar, HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/api/calendars/{id}")
    public ResponseEntity<HttpStatus> deleteCalendar(@PathVariable Integer id) {
        try {
            unifiedCalendarService.deleteCalendar(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ==========================================
    // CalendarEvent Endpoints (formerly in /api/calendar-events)
    // ==========================================

    @PostMapping("/api/calendar-events")
    public ResponseEntity<?> createEvent(
            @RequestBody EventCreationRequest request,
            @RequestHeader(value = "X-Email") String emailHeader) {
        try {
            if (emailHeader == null || emailHeader.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("X-Email header is required");
            }
            Calendar002 createdEvent = unifiedCalendarService.createEvent(emailHeader, request);
            return new ResponseEntity<>(createdEvent, HttpStatus.CREATED);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/api/calendar-events/{orgcode}/{calid}")
    public ResponseEntity<List<Calendar002>> getEventsByCalendar(
            @PathVariable Integer orgcode,
            @PathVariable Integer calid) {
        try {
            List<Calendar002> events = unifiedCalendarService.getEventsByCalendar(orgcode, calid);
            return new ResponseEntity<>(events, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/api/calendar-events/{orgcode}/{calid}/{eventid}/rsvp")
    public ResponseEntity<?> updateRsvp(
            @PathVariable Integer orgcode,
            @PathVariable Integer calid,
            @PathVariable Integer eventid,
            @RequestBody java.util.Map<String, String> payload,
            @RequestHeader(value = "X-Email") String emailHeader) {
        try {
            if (emailHeader == null || emailHeader.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("X-Email header is required");
            }
            String status = payload.get("status");
            if (status == null || status.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Status is required");
            }
            unifiedCalendarService.updateAttendeeResponseStatus(orgcode, calid, eventid, emailHeader, status);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private LocalDateTime parseLocalDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        try {
            String cleaned = dateStr.trim();
            if (cleaned.contains("+")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("+"));
            }
            if (cleaned.endsWith("Z")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            if (cleaned.contains("T")) {
                int dotIdx = cleaned.indexOf(".");
                if (dotIdx != -1) {
                    cleaned = cleaned.substring(0, dotIdx);
                }
                return LocalDateTime.parse(cleaned, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } else if (cleaned.contains(" ")) {
                int dotIdx = cleaned.indexOf(".");
                if (dotIdx != -1) {
                    cleaned = cleaned.substring(0, dotIdx);
                }
                return LocalDateTime.parse(cleaned.replace(" ", "T"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @PutMapping("/api/calendar-events/rsvp-from-email")
    public ResponseEntity<?> updateRsvpFromEmail(
            @RequestBody java.util.Map<String, String> payload,
            @RequestHeader(value = "X-Email") String emailHeader) {
        try {
            if (emailHeader == null || emailHeader.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("X-Email header is required");
            }
            String title = payload.get("title");
            String startTimeStr = payload.get("startTime"); 
            String status = payload.get("status");
            
            if (title == null || startTimeStr == null || status == null) {
                 return ResponseEntity.badRequest().body("Missing required fields");
            }
            
            List<Calendar001> cals = unifiedCalendarService.getCalendarsForUser(emailHeader);
            if (cals.isEmpty()) {
                Calendar001 defaultCal = new Calendar001();
                defaultCal.setCalname("My Calendar");
                defaultCal.setUserid(emailHeader);
                defaultCal.setCuser(emailHeader);
                Calendar001 createdCal = unifiedCalendarService.createCalendar(defaultCal);
                cals = java.util.Collections.singletonList(createdCal);
            }
            
            String normTitle = title.trim().toLowerCase();
            LocalDateTime targetStart = parseLocalDateTime(startTimeStr);
            
            Calendar002 matchedEvent = null;
            Integer matchedOrgcode = null;
            Integer matchedCalid = null;
            
            for (Calendar001 cal : cals) {
                List<Calendar002> events = unifiedCalendarService.getEventsByCalendar(cal.getOrgcode(), cal.getCalid());
                for (Calendar002 e : events) {
                    if (e.getTitle() == null) continue;
                    String eTitle = e.getTitle().trim().toLowerCase();
                    boolean titleMatches = eTitle.equals(normTitle) || eTitle.contains(normTitle) || normTitle.contains(eTitle);
                    
                    if (titleMatches) {
                        if (targetStart != null && e.getStartTime() != null) {
                            if (targetStart.toLocalDate().equals(e.getStartTime().toLocalDate())) {
                                long minutesDiff = Math.abs(java.time.Duration.between(targetStart, e.getStartTime()).toMinutes());
                                if (minutesDiff <= 60) {
                                    matchedEvent = e;
                                    matchedOrgcode = cal.getOrgcode();
                                    matchedCalid = cal.getCalid();
                                    break;
                                }
                            }
                        } else {
                            if (eTitle.equals(normTitle)) {
                                matchedEvent = e;
                                matchedOrgcode = cal.getOrgcode();
                                matchedCalid = cal.getCalid();
                                break;
                            }
                        }
                    }
                }
                if (matchedEvent != null) break;
            }
            
            if (matchedEvent != null) {
                unifiedCalendarService.updateAttendeeResponseStatus(matchedOrgcode, matchedCalid, matchedEvent.getEventid(), emailHeader, status);
                matchedEvent.setStatus(status);
                unifiedCalendarService.updateEvent(matchedCalid, matchedOrgcode, matchedEvent.getEventid(), matchedEvent);
                return new ResponseEntity<>(HttpStatus.OK);
            } else {
                // Auto-create event in local calendar so RSVP succeeds and adds the event to user's calendar!
                Calendar001 primaryCal = cals.get(0);
                Calendar002 newEvent = new Calendar002();
                newEvent.setOrgcode(primaryCal.getOrgcode());
                newEvent.setCalid(primaryCal.getCalid());
                newEvent.setTitle(title);
                newEvent.setStatus(status);
                newEvent.setLocation(payload.getOrDefault("location", ""));
                newEvent.setMeeturl(payload.getOrDefault("meeturl", ""));
                newEvent.setDescription(payload.getOrDefault("description", ""));
                
                LocalDateTime start = targetStart != null ? targetStart : LocalDateTime.now();
                String endTimeStr = payload.get("endTime");
                LocalDateTime end = parseLocalDateTime(endTimeStr);
                if (end == null) {
                    end = start.plusHours(1);
                }
                
                newEvent.setStartTime(start);
                newEvent.setEndTime(end);
                newEvent.setIsAllDay(0);
                newEvent.setCreatedAt(LocalDateTime.now());
                
                EventCreationRequest req = new EventCreationRequest();
                req.setEvent(newEvent);
                
                Calendar003 selfAttendee = new Calendar003();
                selfAttendee.setEmail(emailHeader);
                selfAttendee.setResponseStatus(status);
                selfAttendee.setRestimestamp(LocalDateTime.now());
                selfAttendee.setIsOptional(false);
                
                req.setAttendees(java.util.Collections.singletonList(selfAttendee));
                
                unifiedCalendarService.createEvent(emailHeader, req);
                return new ResponseEntity<>(HttpStatus.OK);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
