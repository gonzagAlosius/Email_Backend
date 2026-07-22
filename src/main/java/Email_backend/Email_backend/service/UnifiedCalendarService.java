package Email_backend.Email_backend.service;

import Email_backend.Email_backend.model.Calendar001;
import Email_backend.Email_backend.model.Calendar002;
import Email_backend.Email_backend.model.Calendar003;
import Email_backend.Email_backend.model.UserEmailConfig;
import Email_backend.Email_backend.repository.Calendar001Repository;
import Email_backend.Email_backend.repository.Calendar002Repository;
import Email_backend.Email_backend.repository.Calendar003Repository;
import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UnifiedCalendarService {

    @Autowired
    private Calendar001Repository calendar001Repository;

    @Autowired
    private Calendar002Repository calendar002Repository;

    @Autowired
    private Calendar003Repository calendar003Repository;

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    public static class EventCreationRequest {
        private Calendar002 event;
        private List<Calendar003> attendees;
        public Calendar002 getEvent() { return event; }
        public void setEvent(Calendar002 event) { this.event = event; }
        public List<Calendar003> getAttendees() { return attendees; }
        public void setAttendees(List<Calendar003> attendees) { this.attendees = attendees; }
    }

    // ==========================================
    // Calendar001 Methods
    // ==========================================

    public Calendar001 createCalendar(Calendar001 calendar) {
        String emailToSearch = calendar.getUserid() != null && calendar.getUserid().contains("@") ? calendar.getUserid() : calendar.getCuser();
        if (emailToSearch != null && emailToSearch.contains("@")) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(emailToSearch);
            if (configOpt.isPresent()) {
                calendar.setOrgcode(configOpt.get().getOrgcode().intValue());
                calendar.setUserid(configOpt.get().getUserId().toString());
            }
        }
        
        calendar.setCdate(LocalDateTime.now());
        if (calendar.getCuser() == null && emailToSearch != null) {
            calendar.setCuser(emailToSearch);
        }
        return calendar001Repository.save(calendar);
    }

    public List<Calendar001> getAllCalendars() {
        return calendar001Repository.findAll();
    }

    public List<Calendar001> getCalendarsForUser(String email) {
        if (email == null || email.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
        if (configOpt.isPresent()) {
            String userIdStr = configOpt.get().getUserId().toString();
            List<Calendar001> cals = calendar001Repository.findByUserId(userIdStr);
            if (cals != null && !cals.isEmpty()) {
                return cals;
            }
        }
        List<Calendar001> emailCals = calendar001Repository.findByUserId(email);
        return emailCals != null ? emailCals : new java.util.ArrayList<>();
    }

    public Optional<Calendar001> getCalendarById(Integer id) {
        return calendar001Repository.findById(id);
    }

    public List<Calendar001> getCalendarsByUserId(String userid) {
        return calendar001Repository.findByUserId(userid);
    }

    public Calendar001 updateCalendar(Integer id, Calendar001 updatedCalendar) {
        Optional<Calendar001> existingOpt = calendar001Repository.findById(id);
        if (existingOpt.isPresent()) {
            Calendar001 existing = existingOpt.get();
            existing.setCalname(updatedCalendar.getCalname());
            existing.setTimezone(updatedCalendar.getTimezone());
            existing.setCountry(updatedCalendar.getCountry());
            existing.setOrgcode(updatedCalendar.getOrgcode());
            
            existing.setEdate(LocalDateTime.now());
            existing.setEuser(updatedCalendar.getEuser() != null ? updatedCalendar.getEuser() : updatedCalendar.getUserid());
            
            calendar001Repository.update(existing);
            return existing;
        }
        throw new RuntimeException("Calendar with id " + id + " not found");
    }

    public void deleteCalendar(Integer id) {
        calendar001Repository.deleteById(id);
    }

    // ==========================================
    // Calendar002 and Calendar003 Methods
    // ==========================================

    @Transactional
    public Calendar002 createEvent(String userEmail, EventCreationRequest request) {
        Calendar002 event = request.getEvent();
        List<Calendar003> attendees = request.getAttendees();

        Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(userEmail);
        if (configOpt.isPresent()) {
            UserEmailConfig config = configOpt.get();
            event.setOrgcode(config.getOrgcode().intValue());
            event.setOrganizerId(1); 
        } else {
            // Fallback for test users or those without config
            if (event.getOrgcode() == null) {
                event.setOrgcode(1);
            }
            if (event.getOrganizerId() == null) {
                event.setOrganizerId(1);
            }
        }

        if (event.getCreatedAt() == null) {
            event.setCreatedAt(LocalDateTime.now());
        }

        // Check for duplicates (same title and start time within 1 minute)
        List<Calendar002> dup = calendar002Repository.findDuplicate(event.getOrgcode(), event.getCalid(), event.getTitle(), event.getStartTime());
        if (dup != null && !dup.isEmpty()) {
            // If duplicate found, attach attendees to the existing event id and return existing
            Calendar002 existing = dup.get(0);
            if (attendees != null && !attendees.isEmpty()) {
                for (Calendar003 attendee : attendees) {
                    attendee.setOrgcode(existing.getOrgcode());
                    attendee.setCalid(existing.getCalid());
                    attendee.setEventId(existing.getEventid());
                }
                calendar003Repository.saveAll(attendees);
            }
            return existing;
        }

        Calendar002 savedEvent = calendar002Repository.save(event);

        if (attendees != null && !attendees.isEmpty()) {
            for (Calendar003 attendee : attendees) {
                attendee.setOrgcode(savedEvent.getOrgcode());
                attendee.setCalid(savedEvent.getCalid());
                attendee.setEventId(savedEvent.getEventid());
            }
            calendar003Repository.saveAll(attendees);
        }

        return savedEvent;
    }

    public List<Calendar002> getEventsByCalendar(Integer orgcode, Integer calid) {
        return calendar002Repository.findByCalid(orgcode, calid);
    }

    public void updateEvent(Integer calid, Integer orgcode, Integer eventid, Calendar002 updatedEvent) {
        updatedEvent.setCalid(calid);
        updatedEvent.setOrgcode(orgcode);
        updatedEvent.setEventid(eventid);
        calendar002Repository.update(updatedEvent);
    }

    @Transactional
    public void deleteEvent(Integer orgcode, Integer calid, Integer eventid) {
        calendar003Repository.deleteByEventId(orgcode, calid, eventid);
        calendar002Repository.delete(orgcode, calid, eventid);
    }

    public void updateAttendeeResponseStatus(Integer orgcode, Integer calid, Integer eventId, String email, String responseStatus) {
        calendar003Repository.updateResponseStatus(orgcode, calid, eventId, email, responseStatus);
    }
}
