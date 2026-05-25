package Email_backend.Email_backend.controller;

import Email_backend.Email_backend.service.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@CrossOrigin(origins = "*")
public class MailController {

    @Autowired
    private MailService mailService;

    @GetMapping("/send-test-mail")
    public String sendTestMail() {
        try {
            mailService.sendHtmlMail(
                    "rahul@testingmail.duckdns.org",
                    "Test from Spring Boot",
                    "<h2>Hello from Spring Boot + SendGrid</h2>");
            return "Mail sent successfully!";
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed: " + e.getMessage();
        }
    }
}
