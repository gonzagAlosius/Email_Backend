package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.dto.EmailRequest;
import Email_backend.Email_backend.dto.EmailResponse;
import Email_backend.Email_backend.service.EmailService;
import Email_backend.Email_backend.service.EmailReceiveService;
import Email_backend.Email_backend.service.OneSignalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import Email_backend.Email_backend.service.EncryptionService;
import Email_backend.Email_backend.service.OrgEmailConfigService;
import Email_backend.Email_backend.model.UserEmailConfig;
import org.springframework.http.HttpStatus;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Base64;

@RestController
@RequestMapping("/api/email")
@CrossOrigin
public class EmailController {

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailReceiveService emailReceiveService;

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private OrgEmailConfigService orgEmailConfigService;

    @Autowired
    private OneSignalService oneSignalService;

    @GetMapping("/inbox")
    public ResponseEntity<?> getInbox(
            @RequestHeader("X-Email") String email,
            @RequestHeader(value = "X-Password", required = false) String password,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {

        String actualPassword = password;
        if (actualPassword == null || actualPassword.trim().isEmpty()) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Password missing from request and database. Please sign in.");
            }
        }

        try {
            List<EmailResponse> inboxData = emailReceiveService.fetchInbox(email, actualPassword, page, size);

            // Save or update credentials to PostgreSQL database if password was provided in
            // the header and authentication succeeded, AND it is not an OAuth token (to
            // avoid overwriting Refresh Tokens)
            if (password != null && !password.trim().isEmpty()
                    && !Email_backend.Email_backend.service.MailConfigDetector.isOAuthToken(password)) {
                try {
                    Optional<UserEmailConfig> existingOpt = userEmailConfigRepository.findByEmailAddress(email);
                    UserEmailConfig configEntity;
                    if (existingOpt.isPresent()) {
                        configEntity = existingOpt.get();
                        configEntity.setEncryptedPassword(encryptionService.encrypt(password));
                        configEntity.setEdate(java.time.LocalDateTime.now());
                        configEntity.setEuser("system");
                    } else {
                        configEntity = new UserEmailConfig();
                        configEntity.setMailboxId(java.util.UUID.randomUUID());

                        Long resolvedOrgcode = 101L;
                        try {
                            Email_backend.Email_backend.service.MailConfigDetector.Config mailConfig = orgEmailConfigService
                                    .getMailConfig(email, actualPassword);
                            if (mailConfig != null && mailConfig.getOrgcode() != null) {
                                resolvedOrgcode = mailConfig.getOrgcode();
                            }
                        } catch (Exception e) {
                            System.err.println(
                                    "Could not resolve orgcode dynamically, defaulting to 101L: " + e.getMessage());
                        }
                        configEntity.setOrgcode(resolvedOrgcode);

                        configEntity.setUserId(java.util.UUID.randomUUID());
                        configEntity.setEmailAddress(email);
                        configEntity.setEncryptedPassword(encryptionService.encrypt(password));
                        configEntity.setIsActive(true);
                        configEntity.setCdate(java.time.LocalDateTime.now());
                        configEntity.setCuser("system");
                    }
                    userEmailConfigRepository.save(configEntity);
                    System.out.println("Successfully saved email credentials to PostgreSQL database for: " + email);
                } catch (Exception dbEx) {
                    System.err
                            .println("Failed to save email configuration to PostgreSQL database: " + dbEx.getMessage());
                    dbEx.printStackTrace();
                }
            }

            return ResponseEntity.ok(inboxData);
        } catch (Exception e) {
            e.printStackTrace();
            if (isAuthFailure(e)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Authentication failed: " + e.getMessage());
            }
            return ResponseEntity.internalServerError().body("Failed to load inbox: " + e.getMessage());
        }
    }

    @GetMapping("/details")
    public ResponseEntity<?> getEmailDetails(
            @RequestParam("folder") String folder,
            @RequestParam("uid") Long uid,
            @RequestHeader("X-Email") String email,
            @RequestHeader(value = "X-Password", required = false) String password) {

        String actualPassword = password;
        if (actualPassword == null || actualPassword.trim().isEmpty()) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Password missing from request and database. Please sign in.");
            }
        }

        try {
            String imapFolder = folder;
            if (folder.equalsIgnoreCase("Inbox")) {
                imapFolder = "INBOX";
            }
            return ResponseEntity
                    .ok(emailReceiveService.fetchEmailDetailsByUid(imapFolder, uid, email, actualPassword));
        } catch (Exception e) {
            e.printStackTrace();
            if (isAuthFailure(e)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Authentication failed: " + e.getMessage());
            }
            return ResponseEntity.internalServerError().body("Failed to load message details: " + e.getMessage());
        }
    }

    @GetMapping("/attachment")
    public ResponseEntity<?> getAttachment(
            @RequestParam("folder") String folder,
            @RequestParam("uid") Long uid,
            @RequestParam("fileName") String fileName,
            @RequestHeader("X-Email") String email,
            @RequestHeader(value = "X-Password", required = false) String password) {

        String actualPassword = password;
        if (actualPassword == null || actualPassword.trim().isEmpty()) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Password missing.");
            }
        }

        try {
            String imapFolder = folder;
            if (folder.equalsIgnoreCase("Inbox")) {
                imapFolder = "INBOX";
            }
            byte[] data = emailReceiveService.fetchAttachmentBytes(imapFolder, uid, fileName, email, actualPassword);
            if (data == null) {
                return ResponseEntity.notFound().build();
            }
            String base64Data = Base64.getEncoder().encodeToString(data);
            Map<String, String> response = new HashMap<>();
            response.put("base64Data", base64Data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to load attachment: " + e.getMessage());
        }
    }

    @GetMapping("/sent")
    public ResponseEntity<?> getSentMessages(
            @RequestHeader("X-Email") String email,
            @RequestHeader(value = "X-Password", required = false) String password,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {

        String actualPassword = password;
        if (actualPassword == null || actualPassword.trim().isEmpty()) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Password missing from request and database. Please sign in.");
            }
        }

        try {
            return ResponseEntity.ok(emailReceiveService.fetchSentMessages(email, actualPassword, page, size));
        } catch (Exception e) {
            e.printStackTrace();
            if (isAuthFailure(e)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Authentication failed: " + e.getMessage());
            }
            return ResponseEntity.internalServerError().body("Failed to load sent messages: " + e.getMessage());
        }
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendEmail(
            @RequestHeader("X-Email") String email,
            @RequestHeader(value = "X-Password", required = false) String password,
            @RequestBody EmailRequest emailRequest) {

        String actualPassword = password;
        if (actualPassword == null || actualPassword.trim().isEmpty()) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Password missing from request and database. Please sign in.");
            }
        }

        try {
            emailService.sendEmail(emailRequest, email, actualPassword);

            // Send a push notification to the recipient(s) if they are registered users
            try {
                String recipientEmail = emailRequest.getTo();
                String senderName = email; // Use sender email as display name
                String subject = emailRequest.getSubject() != null
                        ? emailRequest.getSubject()
                        : "(No Subject)";
                String pushBody = "New email from " + senderName + ": " + subject;

                if (recipientEmail != null && !recipientEmail.isEmpty()) {
                    String[] recipients = recipientEmail.split("[,;]");
                    for (String rec : recipients) {
                        rec = rec.trim();
                        if (!rec.isEmpty()) {
                            // external_id in OneSignal is set to the user's email via OneSignal.login()
                            oneSignalService.sendPushToExternalId(
                                    rec,
                                    "📬 New Email",
                                    pushBody,
                                    senderName);
                        }
                    }
                }
            } catch (Exception pushEx) {
                // Non-critical: log but don't fail the send operation
                System.err.println("[OneSignal] Failed to send push after email dispatch: "
                        + pushEx.getMessage());
            }

            return ResponseEntity.ok("Email sent successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            if (isAuthFailure(e)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Authentication failed: " + e.getMessage());
            }
            return ResponseEntity.internalServerError().body("Failed to send email: " + e.getMessage());
        }
    }

    private boolean isAuthFailure(Throwable e) {
        if (e == null)
            return false;
        if (e instanceof javax.mail.AuthenticationFailedException) {
            return true;
        }
        if (e.getMessage() != null
                && (e.getMessage().contains("AUTHENTICATIONFAILED") || e.getMessage().contains("Authentication failed")
                        || e.getMessage().contains("Invalid credentials"))) {
            return true;
        }
        return isAuthFailure(e.getCause());
    }
}
