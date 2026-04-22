package Email_backend.Email_backend.service;

import Email_backend.Email_backend.dto.EmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ByteArrayResource;

import javax.mail.internet.MimeMessage;
import java.util.Base64;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendEmail(EmailRequest emailRequest) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true); // true = multipart

            helper.setFrom("sampletestingmail1@gmail.com");
            helper.setTo(emailRequest.getTo());
            helper.setSubject(emailRequest.getSubject());
            helper.setText(emailRequest.getContent());

            if (emailRequest.getAttachments() != null) {
                for (EmailRequest.AttachmentRequest att : emailRequest.getAttachments()) {
                    byte[] data = Base64.getDecoder().decode(att.getBase64Content());
                    helper.addAttachment(att.getFileName(), new ByteArrayResource(data));
                }
            }

            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }
}
