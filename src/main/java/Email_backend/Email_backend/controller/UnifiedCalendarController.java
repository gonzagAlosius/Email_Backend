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
    public ResponseEntity<List<Calendar001>> getAllCalendars() {
        try {
            List<Calendar001> calendars = unifiedCalendarService.getAllCalendars();
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
}
