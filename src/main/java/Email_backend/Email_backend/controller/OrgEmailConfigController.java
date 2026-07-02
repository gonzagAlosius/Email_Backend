package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.model.OrgEmailConfig;
import Email_backend.Email_backend.model.UserEmailConfig;
import Email_backend.Email_backend.repository.OrgEmailConfigRepository;
import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/org-config")
@CrossOrigin(origins = "*")
public class OrgEmailConfigController {

    @Autowired
    private OrgEmailConfigRepository orgEmailConfigRepository;

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/check/{orgCode}")
    public ResponseEntity<?> checkByOrgCode(@PathVariable Long orgCode) {
        try {
            String sql = "SELECT orgcode, imap_host, imap_port, imap_secure, smtp_host, smtp_port, smtp_secure " +
                    "FROM mail101 WHERE orgcode = ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, orgCode);

            boolean exists = false;
            if (rows != null && !rows.isEmpty()) {
                for (Map<String, Object> row : rows) {
                    String imapHost = (String) row.get("imap_host");
                    String smtpHost = (String) row.get("smtp_host");
                    if (imapHost != null && !imapHost.trim().isEmpty() &&
                            smtpHost != null && !smtpHost.trim().isEmpty()) {
                        exists = true;
                        break;
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("exists", exists);
            response.put("orgCode", orgCode);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("exists", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/check-by-email")
    public ResponseEntity<?> checkByEmail(@RequestParam String email) {
        try {
            Optional<UserEmailConfig> userConfig = userEmailConfigRepository.findByEmailAddress(email);
            if (userConfig.isPresent()) {
                Long orgcode = userConfig.get().getOrgcode();
                if (orgcode != null) {
                    String sql = "SELECT orgcode, imap_host, imap_port, imap_secure, smtp_host, smtp_port, smtp_secure "
                            +
                            "FROM mail101 WHERE orgcode = ?";
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, orgcode);

                    boolean exists = false;
                    if (rows != null && !rows.isEmpty()) {
                        for (Map<String, Object> row : rows) {
                            String imapHost = (String) row.get("imap_host");
                            String smtpHost = (String) row.get("smtp_host");
                            if (imapHost != null && !imapHost.trim().isEmpty() &&
                                    smtpHost != null && !smtpHost.trim().isEmpty()) {
                                exists = true;
                                break;
                            }
                        }
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("exists", exists);
                    response.put("orgCode", orgcode);
                    return ResponseEntity.ok(response);
                }
            }
            Map<String, Object> response = new HashMap<>();
            response.put("exists", false);
            response.put("error", "Organization code not found for this email address");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("exists", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveConfig(@RequestBody OrgEmailConfig incoming) {
        try {
            if (incoming.getOrgcode() == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "Organization code (orgcode) is required");
                return ResponseEntity.status(400).body(response);
            }

            // Delete any existing rows for this orgcode first to clean up duplicate entries
            jdbcTemplate.update("DELETE FROM mail101 WHERE orgcode = ?", incoming.getOrgcode());

            // Insert single clean record
            String insertSql = "INSERT INTO mail101 (orgcode, imap_host, imap_port, imap_secure, smtp_host, smtp_port, smtp_secure, cdate, cuser, edate, euser) "
                    +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            jdbcTemplate.update(insertSql,
                    incoming.getOrgcode(),
                    incoming.getImapHost(),
                    incoming.getImapPort(),
                    incoming.getImapSecure() != null ? incoming.getImapSecure() : true,
                    incoming.getSmtpHost(),
                    incoming.getSmtpPort(),
                    incoming.getSmtpSecure() != null ? incoming.getSmtpSecure() : true,
                    now,
                    "SYSTEM",
                    now,
                    "SYSTEM");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orgCode", incoming.getOrgcode());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/delete/{orgCode}")
    public ResponseEntity<?> deleteConfig(@PathVariable Long orgCode) {
        try {
            jdbcTemplate.update("DELETE FROM mail101 WHERE orgcode = ?", orgCode);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orgCode", orgCode);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
