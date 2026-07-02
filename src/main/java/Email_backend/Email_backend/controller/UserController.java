package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.model.UserEmailConfig;
import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import Email_backend.Email_backend.service.EncryptionService;
import Email_backend.Email_backend.service.MailConfigDetector;
import Email_backend.Email_backend.service.MicrosoftGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")

public class UserController {

    @Autowired
    private MicrosoftGraphService microsoftGraphService;

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    @Autowired
    private EncryptionService encryptionService;

    @GetMapping
    public ResponseEntity<?> getAllUsers(
            @RequestHeader(value = "X-Email", required = false) String email,
            @RequestHeader(value = "X-Password", required = false) String password) {

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(401).body("Email header is missing");
        }

        String actualPassword = password;
        if (email != null && !email.trim().isEmpty() && (actualPassword == null || actualPassword.trim().isEmpty())) {
            Optional<UserEmailConfig> configOpt = userEmailConfigRepository.findByEmailAddress(email);
            if (configOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(configOpt.get().getEncryptedPassword());
            }
        }

        List<String> users = new ArrayList<>();
        if (email != null && actualPassword != null) {
            String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : "";
            if (MailConfigDetector.isMicrosoftDomain(domain)) {
                String graphToken = MailConfigDetector.resolveGraphPassword(email, actualPassword);
                if (graphToken != null && MailConfigDetector.isOAuthToken(graphToken)) {
                    users = microsoftGraphService.fetchAllUsers(graphToken);
                }
            }
        }

        return ResponseEntity.ok(users);
    }
}
