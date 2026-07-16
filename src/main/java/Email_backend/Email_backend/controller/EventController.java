package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.dto.EventRequest;
import Email_backend.Email_backend.model.Event;
import Email_backend.Email_backend.service.EventService;
import Email_backend.Email_backend.model.UserEmailConfig;
import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import Email_backend.Email_backend.service.EncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/events")
@CrossOrigin
public class EventController {

    @Autowired
    private EventService service;

    @Autowired
    private Email_backend.Email_backend.service.UnifiedCalendarService unifiedCalendarService;

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    @Autowired
    private EncryptionService encryptionService;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody EventRequest req,
            @RequestHeader(value = "X-Email", required = false) String email,
            @RequestHeader(value = "X-Password", required = false) String password) {
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email header is missing");
        }

        String actualPassword = password;
        if (email != null && !email.trim().isEmpty() && (actualPassword == null || actualPassword.trim().isEmpty())) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            }
        }

        service.createEvent(req, email, actualPassword);
        return ResponseEntity.ok("Event created successfully + Synced to Graph (if applicable)");
    }

    // GET ALL
    @GetMapping
    public ResponseEntity<?> getAllEvents(
            @RequestHeader(value = "X-Email", required = false) String email,
            @RequestHeader(value = "X-Password", required = false) String password,
            @RequestParam(required = false) Integer calid,
            @RequestParam(required = false) Integer orgcode) {
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email header is missing");
        }

        String actualPassword = password;
        if (email != null && !email.trim().isEmpty() && (actualPassword == null || actualPassword.trim().isEmpty())) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            }
        }
        
        return ResponseEntity.ok(service.getAllEvents(email, actualPassword, calid, orgcode));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody EventRequest req,
            @RequestHeader(value = "X-Email", required = false) String email,
            @RequestHeader(value = "X-Password", required = false) String password) {
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email header is missing");
        }

        String actualPassword = password;
        if (email != null && !email.trim().isEmpty() && (actualPassword == null || actualPassword.trim().isEmpty())) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            }
        }

        try {
            if (req.getCalid() != null && req.getOrgcode() != null) {
                Email_backend.Email_backend.model.Calendar002 cal2 = new Email_backend.Email_backend.model.Calendar002();
                cal2.setCalid(req.getCalid());
                cal2.setOrgcode(req.getOrgcode());
                cal2.setEventid(id.intValue());
                cal2.setTitle(req.getTitle());
                cal2.setDescription(req.getDescription());
                cal2.setLocation(req.getLocation());
                cal2.setStartTime(req.getStartTime());
                cal2.setEndTime(req.getEndTime());
                cal2.setIsAllDay(req.isAllDay() ? 1 : 0);
                cal2.setRecurrenceRule(req.getRecurrence());
                cal2.setStatus("CONFIRMED");
                cal2.setMeeturl(req.getMeeturl());
                unifiedCalendarService.updateEvent(req.getCalid(), req.getOrgcode(), id.intValue(), cal2);
            } else {
                service.updateEvent(id, req, email, actualPassword);
            }
            return ResponseEntity.ok("Event updated successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating event: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-Email", required = false) String email,
            @RequestHeader(value = "X-Password", required = false) String password,
            @RequestParam(required = false) Integer calid,
            @RequestParam(required = false) Integer orgcode) {
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email header is missing");
        }

        String actualPassword = password;
        if (email != null && !email.trim().isEmpty() && (actualPassword == null || actualPassword.trim().isEmpty())) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            }
        }

        try {
            if (calid != null && orgcode != null) {
                unifiedCalendarService.deleteEvent(orgcode, calid, id.intValue());
            } else {
                service.deleteEvent(id, email, actualPassword);
            }
            return ResponseEntity.ok("Event deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting event: " + e.getMessage());
        }
    }

    @GetMapping("/graph/{graphEventId}")
    public ResponseEntity<?> getGraphEvent(
            @PathVariable String graphEventId,
            @RequestHeader(value = "X-Email", required = false) String email,
            @RequestHeader(value = "X-Password", required = false) String password) {
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email header is missing");
        }

        String actualPassword = password;
        if (email != null && !email.trim().isEmpty() && (actualPassword == null || actualPassword.trim().isEmpty())) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            }
        }
        
        try {
            java.util.Map<String, Object> event = service.getGraphEventById(graphEventId, email, actualPassword);
            if (event != null) {
                return ResponseEntity.ok(event);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Event not found in MS Graph or invalid credentials.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching graph event: " + e.getMessage());
        }
    }

    @PutMapping("/graph/{graphEventId}/rsvp")
    public ResponseEntity<?> updateGraphRsvp(
            @PathVariable String graphEventId,
            @RequestBody java.util.Map<String, String> body,
            @RequestHeader(value = "X-Email", required = false) String email,
            @RequestHeader(value = "X-Password", required = false) String password) {
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email header is missing");
        }

        String actualPassword = password;
        if (email != null && !email.trim().isEmpty() && (actualPassword == null || actualPassword.trim().isEmpty())) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            }
        }

        String status = body.get("status");
        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Status is missing in request body");
        }

        try {
            service.updateExternalRsvp(graphEventId, email, actualPassword, status);
            return ResponseEntity.ok("RSVP updated on external calendar successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update RSVP on external calendar: " + e.getMessage());
        }
    }
}
