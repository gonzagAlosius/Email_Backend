package Email_backend.Email_backend.repository;

import Email_backend.Email_backend.model.Calendar002;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class Calendar002Repository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<Calendar002> rowMapper = (rs, rowNum) -> {
        Calendar002 event = new Calendar002();
        event.setOrgcode(rs.getInt("orgcode"));
        event.setCalid(rs.getInt("calid"));
        event.setEventid(rs.getInt("eventid"));
        event.setOrganizerId(rs.getObject("organizer_id") != null ? rs.getInt("organizer_id") : null);
        event.setTitle(rs.getString("title"));
        event.setDescription(rs.getString("description"));
        event.setLocation(rs.getString("location"));
        event.setStartTime(rs.getTimestamp("start_time") != null ? rs.getTimestamp("start_time").toLocalDateTime() : null);
        event.setEndTime(rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toLocalDateTime() : null);
        event.setIsAllDay(rs.getObject("is_all_day") != null ? rs.getInt("is_all_day") : null);
        event.setIsRecurring(rs.getObject("is_recurring") != null ? rs.getInt("is_recurring") : null);
        event.setRecurrenceRule(rs.getString("recurrence_rule"));
        event.setMeeturl(rs.getString("meeturl"));
        event.setEnddate(rs.getDate("enddate") != null ? rs.getDate("enddate").toLocalDate() : null);
        event.setStatus(rs.getString("status"));
        event.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        event.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return event;
    };

    public Calendar002 save(Calendar002 event) {
        String maxIdSql = "SELECT COALESCE(MAX(eventid), 0) + 1 FROM calender_dev.calendar002 WHERE orgcode = ? AND calid = ?";
        Integer nextEventId = jdbcTemplate.queryForObject(maxIdSql, Integer.class, event.getOrgcode(), event.getCalid());
        event.setEventid(nextEventId);

        String sql = "INSERT INTO calender_dev.calendar002 (orgcode, calid, eventid, organizer_id, title, description, location, " +
                     "start_time, end_time, is_all_day, is_recurring, recurrence_rule, meeturl, enddate, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, event.getOrgcode());
            ps.setObject(2, event.getCalid());
            ps.setObject(3, event.getEventid());
            ps.setObject(4, event.getOrganizerId());
            ps.setString(5, event.getTitle());
            ps.setString(6, event.getDescription());
            ps.setString(7, event.getLocation());
            ps.setTimestamp(8, event.getStartTime() != null ? Timestamp.valueOf(event.getStartTime()) : null);
            ps.setTimestamp(9, event.getEndTime() != null ? Timestamp.valueOf(event.getEndTime()) : null);
            ps.setObject(10, event.getIsAllDay());
            ps.setObject(11, event.getIsRecurring());
            ps.setString(12, event.getRecurrenceRule());
            ps.setString(13, event.getMeeturl());
            ps.setDate(14, event.getEnddate() != null ? java.sql.Date.valueOf(event.getEnddate()) : null);
            ps.setString(15, event.getStatus());
            ps.setTimestamp(16, event.getCreatedAt() != null ? Timestamp.valueOf(event.getCreatedAt()) : null);
            return ps;
        });

        return event;
    }

    public List<Calendar002> findByCalid(Integer orgcode, Integer calid) {
        String sql = "SELECT * FROM calender_dev.calendar002 WHERE orgcode = ? AND calid = ?";
        return jdbcTemplate.query(sql, rowMapper, orgcode, calid);
    }
}
