package Email_backend.Email_backend;

import Email_backend.Email_backend.model.UserEmailConfig;
import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import Email_backend.Email_backend.service.EncryptionService;
import Email_backend.Email_backend.service.MailConfigDetector;
import Email_backend.Email_backend.service.MicrosoftGraphService;
import Email_backend.Email_backend.model.Event;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;
import java.util.List;

@Component
public class GraphTestRunner implements CommandLineRunner {

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private MicrosoftGraphService microsoftGraphService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("====== GRAPH TEST RUNNER START ======");
        // Test disabled
        System.out.println("====== GRAPH TEST RUNNER END ======");

    }
}
