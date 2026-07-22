package Email_backend.Email_backend.repository;

import Email_backend.Email_backend.model.Calendar001;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class Calendar001Repository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<Calendar001> rowMapper = (rs, rowNum) -> {
        Calendar001 calendar = new Calendar001();
        calendar.setCalid(rs.getInt("calid"));
        calendar.setOrgcode(rs.getInt("orgcode"));
        calendar.setUserid(rs.getString("userid"));
        calendar.setCalname(rs.getString("calname"));
        calendar.setTimezone(rs.getString("timezone"));
        calendar.setCountry(rs.getString("country"));
        calendar.setEuser(rs.getString("euser"));
        
        Timestamp edate = rs.getTimestamp("edate");
        if (edate != null) {
            calendar.setEdate(edate.toLocalDateTime());
        }

        calendar.setCuser(rs.getString("cuser"));
        
        Timestamp cdate = rs.getTimestamp("cdate");
        if (cdate != null) {
            calendar.setCdate(cdate.toLocalDateTime());
        }

        calendar.setAuser(rs.getString("auser"));
        
        Timestamp adate = rs.getTimestamp("adate");
        if (adate != null) {
            calendar.setAdate(adate.toLocalDateTime());
        }

        return calendar;
    };

    public Calendar001 save(Calendar001 calendar) {
        // Since calid is constrained by a unique key (orgcode, calid), 
        // we must generate the next calid per orgcode, not per user.
        String maxIdSql = "SELECT COALESCE(MAX(calid), 0) + 1 FROM calendar001 WHERE orgcode = ?";
        Integer nextCalid = jdbcTemplate.queryForObject(maxIdSql, Integer.class, calendar.getOrgcode());
        calendar.setCalid(nextCalid);

        String sql = "INSERT INTO calendar001 (orgcode, userid, calid, calname, timezone, country, cuser, cdate) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                     
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, calendar.getOrgcode());
            ps.setString(2, calendar.getUserid());
            ps.setInt(3, calendar.getCalid());
            ps.setString(4, calendar.getCalname());
            ps.setString(5, calendar.getTimezone());
            ps.setString(6, calendar.getCountry());
            ps.setString(7, calendar.getCuser());
            ps.setTimestamp(8, calendar.getCdate() != null ? Timestamp.valueOf(calendar.getCdate()) : null);
            return ps;
        });

        return calendar;
    }

    public List<Calendar001> findAll() {
        String sql = "SELECT * FROM calendar001";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public Optional<Calendar001> findById(Integer calid) {
        String sql = "SELECT * FROM calendar001 WHERE calid = ?";
        List<Calendar001> results = jdbcTemplate.query(sql, rowMapper, calid);
        return results.stream().findFirst();
    }

    public List<Calendar001> findByUserId(String userid) {
        String sql = "SELECT * FROM calendar001 WHERE userid = ?";
        return jdbcTemplate.query(sql, rowMapper, userid);
    }

    public List<Calendar001> findByOrgcode(Integer orgcode) {
        String sql = "SELECT * FROM calendar001 WHERE orgcode = ?";
        return jdbcTemplate.query(sql, rowMapper, orgcode);
    }

    public int update(Calendar001 calendar) {
        String sql = "UPDATE calendar001 SET orgcode = ?, userid = ?, calname = ?, timezone = ?, " +
                     "country = ?, euser = ?, edate = ?, auser = ?, adate = ? WHERE calid = ?";
        return jdbcTemplate.update(sql,
                calendar.getOrgcode(),
                calendar.getUserid(),
                calendar.getCalname(),
                calendar.getTimezone(),
                calendar.getCountry(),
                calendar.getEuser(),
                calendar.getEdate() != null ? Timestamp.valueOf(calendar.getEdate()) : null,
                calendar.getAuser(),
                calendar.getAdate() != null ? Timestamp.valueOf(calendar.getAdate()) : null,
                calendar.getCalid());
    }

    public int deleteById(Integer calid) {
        String sql = "DELETE FROM calendar001 WHERE calid = ?";
        return jdbcTemplate.update(sql, calid);
    }
}
