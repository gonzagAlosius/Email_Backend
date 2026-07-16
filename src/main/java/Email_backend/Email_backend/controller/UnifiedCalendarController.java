package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.model.Calendar001;
import Email_backend.Email_backend.model.Calendar002;
import Email_backend.Email_backend.service.UnifiedCalendarService;
import Email_backend.Email_backend.service.UnifiedCalendarService.EventCreationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            
            String startTimePrefix = startTimeStr;
            if (startTimePrefix.length() > 16) {
                startTimePrefix = startTimePrefix.substring(0, 16);
            }
            
            List<Calendar001> cals = unifiedCalendarService.getCalendarsForUser(emailHeader);
            if (cals.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No calendar found");
            
            Integer orgcode = cals.get(0).getOrgcode();
            Integer calid = cals.get(0).getCalid();
            
            List<Calendar002> events = unifiedCalendarService.getEventsByCalendar(orgcode, calid);
            Calendar002 matchedEvent = null;
            for (Calendar002 e : events) {
                if (e.getTitle() != null && e.getTitle().equals(title) && e.getStartTime() != null) {
                     String eStartTime = e.getStartTime().toString();
                     if (eStartTime.startsWith(startTimePrefix) || startTimePrefix.startsWith(eStartTime)) {
                          matchedEvent = e;
                          break;
                     }
                }
            }
            
            if (matchedEvent != null) {
                unifiedCalendarService.updateAttendeeResponseStatus(orgcode, calid, matchedEvent.getEventid(), emailHeader, status);
                return new ResponseEntity<>(HttpStatus.OK);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Event not found in local calendar");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
