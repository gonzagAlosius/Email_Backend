package Email_backend.Email_backend.repository;

import Email_backend.Email_backend.model.Calendar003;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class Calendar003Repository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void saveAll(List<Calendar003> attendees) {
        if (attendees == null || attendees.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO calender_dev.calendar003 (orgcode, calid, event_id, email, display_name, response_status, restimestamp, is_optional) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                     
        jdbcTemplate.batchUpdate(sql, attendees, attendees.size(), (PreparedStatement ps, Calendar003 attendee) -> {
            ps.setObject(1, attendee.getOrgcode());
            ps.setObject(2, attendee.getCalid());
            ps.setObject(3, attendee.getEventId());
            ps.setString(4, attendee.getEmail());
            ps.setString(5, attendee.getDisplayName());
            ps.setString(6, attendee.getResponseStatus());
            ps.setTimestamp(7, attendee.getRestimestamp() != null ? Timestamp.valueOf(attendee.getRestimestamp()) : null);
            ps.setObject(8, attendee.getIsOptional());
        });
    }

    public void updateResponseStatus(Integer orgcode, Integer calid, Integer eventId, String email, String responseStatus) {
        String sql = "UPDATE calender_dev.calendar003 SET response_status = ?, restimestamp = NOW() " +
                     "WHERE orgcode = ? AND calid = ? AND event_id = ? AND email = ?";
        jdbcTemplate.update(sql, responseStatus, orgcode, calid, eventId, email);
    }

    public void deleteByEventId(Integer orgcode, Integer calid, Integer eventId) {
        String sql = "DELETE FROM calender_dev.calendar003 WHERE orgcode = ? AND calid = ? AND event_id = ?";
        jdbcTemplate.update(sql, orgcode, calid, eventId);
    }
}
