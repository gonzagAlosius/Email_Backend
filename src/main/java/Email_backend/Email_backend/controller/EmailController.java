package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.dto.EmailRequest;
import Email_backend.Email_backend.service.EmailService;
import Email_backend.Email_backend.service.EmailReceiveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email")
@CrossOrigin(origins = "*")
public class EmailController {

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailReceiveService emailReceiveService;

    @GetMapping("/inbox")
    public ResponseEntity<?> getInbox() {
        try {
            return ResponseEntity.ok(emailReceiveService.fetchInbox());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to load inbox: " + e.getMessage());
        }
    }

    @GetMapping("/sent")
    public ResponseEntity<?> getSentMessages() {
        try {
            return ResponseEntity.ok(emailReceiveService.fetchSentMessages());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to load sent messages: " + e.getMessage());
        }
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendEmail(@RequestBody EmailRequest emailRequest) {
        try {
            emailService.sendEmail(emailRequest);
            return ResponseEntity.ok("Email sent successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to send email: " + e.getMessage());
        }
    }
}
