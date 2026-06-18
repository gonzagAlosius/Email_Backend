package Email_backend.Email_backend.service;

import Email_backend.Email_backend.model.OrgEmailConfig;
import Email_backend.Email_backend.model.UserEmailConfig;
import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class OrgEmailConfigService {

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public MailConfigDetector.Config getMailConfig(String email, String resolvedPassword) {
        try {
            Long orgUuid = null;
            Optional<UserEmailConfig> userConfigOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (userConfigOpt.isPresent() && userConfigOpt.get().getOrgcode() != null) {
                orgUuid = userConfigOpt.get().getOrgcode();
            }

            String sql = "SELECT orgcode, imap_host, imap_port, imap_secure, smtp_host, smtp_port, smtp_secure " +
                    "FROM mail101 WHERE orgcode = ?";

            java.util.List<OrgEmailConfig> configs = new java.util.ArrayList<>();
            if (orgUuid != null) {
                configs = jdbcTemplate.query(sql, (rs, rowNum) -> {
                    OrgEmailConfig config = new OrgEmailConfig();
                    long codeVal = rs.getLong("orgcode");
                    if (!rs.wasNull()) {
                        config.setOrgcode(codeVal);
                    }
                    config.setImapHost(rs.getString("imap_host"));

                    int imapP = rs.getInt("imap_port");
                    config.setImapPort(rs.wasNull() ? null : imapP);

                    boolean imapSec = rs.getBoolean("imap_secure");
                    config.setImapSecure(rs.wasNull() ? null : imapSec);

                    config.setSmtpHost(rs.getString("smtp_host"));

                    int smtpP = rs.getInt("smtp_port");
                    config.setSmtpPort(rs.wasNull() ? null : smtpP);

                    boolean smtpSec = rs.getBoolean("smtp_secure");
                    config.setSmtpSecure(rs.wasNull() ? null : smtpSec);

                    return config;
                }, orgUuid);
            }

            OrgEmailConfig orgConfig = selectBestConfig(configs, email);

            if (orgConfig == null) {
                // Try querying ALL rows from mail101 table to find a matching config for this
                // domain
                String sqlAll = "SELECT orgcode, imap_host, imap_port, imap_secure, smtp_host, smtp_port, smtp_secure FROM mail101";
                java.util.List<OrgEmailConfig> allConfigs = jdbcTemplate.query(sqlAll, (rs, rowNum) -> {
                    OrgEmailConfig config = new OrgEmailConfig();
                    long codeVal = rs.getLong("orgcode");
                    if (!rs.wasNull()) {
                        config.setOrgcode(codeVal);
                    }
                    config.setImapHost(rs.getString("imap_host"));

                    int imapP = rs.getInt("imap_port");
                    config.setImapPort(rs.wasNull() ? null : imapP);

                    boolean imapSec = rs.getBoolean("imap_secure");
                    config.setImapSecure(rs.wasNull() ? null : imapSec);

                    config.setSmtpHost(rs.getString("smtp_host"));

                    int smtpP = rs.getInt("smtp_port");
                    config.setSmtpPort(rs.wasNull() ? null : smtpP);

                    boolean smtpSec = rs.getBoolean("smtp_secure");
                    config.setSmtpSecure(rs.wasNull() ? null : smtpSec);

                    return config;
                });
                orgConfig = selectBestConfig(allConfigs, email);
            }

            if (orgConfig == null) {
                throw new IllegalStateException(
                        "Mail server configuration not found in database (mail101 table) for organization of user: "
                                + email);
            }

            String imapHost = orgConfig.getImapHost();
            String imapPort = orgConfig.getImapPort() != null ? String.valueOf(orgConfig.getImapPort()) : null;
            Boolean imapSecure = orgConfig.getImapSecure() != null ? orgConfig.getImapSecure() : true;
            String smtpHost = orgConfig.getSmtpHost();
            String smtpPort = orgConfig.getSmtpPort() != null ? String.valueOf(orgConfig.getSmtpPort()) : null;
            Boolean smtpSecure = orgConfig.getSmtpSecure() != null ? orgConfig.getSmtpSecure() : true;

            String resolvedSentFolder = "Sent";
            if (imapHost != null) {
                if (imapHost.contains("gmail.com")) {
                    resolvedSentFolder = "[Gmail]/Sent Mail";
                } else if (imapHost.contains("office365.com") || imapHost.contains("outlook")) {
                    resolvedSentFolder = "Sent Items";
                }
            }
            return new MailConfigDetector.Config(orgConfig.getOrgcode(), imapHost, imapPort, imapSecure, smtpHost,
                    smtpPort, smtpSecure, resolvedSentFolder);

        } catch (Exception e) {
            System.err.println("Error loading org-specific mail configuration via JDBC: " + e.getMessage());
            throw new RuntimeException("Failed to load mail configuration: " + e.getMessage(), e);
        }
    }

    private OrgEmailConfig selectBestConfig(java.util.List<OrgEmailConfig> configs, String email) {
        if (configs == null || configs.isEmpty()) {
            return null;
        }
        if (configs.size() == 1) {
            return configs.get(0);
        }

        String domain = email.substring(email.indexOf("@") + 1).toLowerCase().trim();

        // 1. Check if Microsoft domain
        boolean isMs = MailConfigDetector.isMicrosoftDomain(domain);

        // 2. Check if Gmail domain
        boolean isGmail = domain.equals("gmail.com") || domain.equals("googlemail.com");

        // Try to match based on provider type
        for (OrgEmailConfig config : configs) {
            String imapHost = config.getImapHost() != null ? config.getImapHost().toLowerCase() : "";
            String smtpHost = config.getSmtpHost() != null ? config.getSmtpHost().toLowerCase() : "";

            if (isMs) {
                if (imapHost.contains("office365") || imapHost.contains("outlook") ||
                        smtpHost.contains("office365") || smtpHost.contains("outlook")) {
                    return config;
                }
            } else if (isGmail) {
                if (imapHost.contains("gmail") || imapHost.contains("google") ||
                        smtpHost.contains("gmail") || smtpHost.contains("google")) {
                    return config;
                }
            } else {
                // For Bluehost / custom domains, avoid office365/outlook/gmail hosts
                if (!imapHost.contains("office365") && !imapHost.contains("outlook") &&
                        !imapHost.contains("gmail") && !imapHost.contains("google")) {
                    return config;
                }
            }
        }

        // Fallback: If no perfect match found, check if any host contains the email
        // domain itself
        for (OrgEmailConfig config : configs) {
            String imapHost = config.getImapHost() != null ? config.getImapHost().toLowerCase() : "";
            if (imapHost.contains(domain)) {
                return config;
            }
        }

        // Hard fallback: just return the first configuration
        return configs.get(0);
    }
}
