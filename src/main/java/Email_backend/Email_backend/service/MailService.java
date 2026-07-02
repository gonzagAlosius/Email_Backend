package Email_backend.Email_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import Email_backend.Email_backend.model.UserEmailConfig;
import Email_backend.Email_backend.service.EncryptionService;

import javax.mail.internet.MimeMessage;
import java.util.Optional;
import java.util.Properties;

@Service
public class MailService {

    @Autowired
    private OrgEmailConfigService orgEmailConfigService;

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    @Autowired
    private EncryptionService encryptionService;

    public void sendHtmlMail(String to, String subject, String html) throws Exception {
        String fromEmail = "amit@testingmail.duckdns.org";

        String password = null;
        Optional<UserEmailConfig> userConfigOpt = userEmailConfigRepository.findByEmailAddress(fromEmail);
        if (userConfigOpt.isPresent()) {
            String encryptedPassword = userConfigOpt.get().getEncryptedPassword();
            password = encryptionService.decrypt(encryptedPassword);
        }

        MailConfigDetector.Config config = orgEmailConfigService.getMailConfig(fromEmail, password);

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getSmtpHost());
        if (config.getSmtpPort() != null) {
            mailSender.setPort(Integer.parseInt(config.getSmtpPort()));
        }
        mailSender.setUsername(fromEmail);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");

        int port = config.getSmtpPort() != null ? Integer.parseInt(config.getSmtpPort()) : 587;
        if (port == 465 || (config.getSmtpSecure() != null && config.getSmtpSecure() && port != 587)) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.ssl.trust", config.getSmtpHost());
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);

        mailSender.send(message);
    }

    public void sendEventInvite(String fromEmail, String password, String to, String subject, String text,
            String icsContent) throws Exception {
        if (fromEmail == null || password == null) {
            System.err.println("Cannot send email: fromEmail or password is null.");
            return;
        }

        MailConfigDetector.Config config = orgEmailConfigService.getMailConfig(fromEmail, password);

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getSmtpHost());
        if (config.getSmtpPort() != null) {
            mailSender.setPort(Integer.parseInt(config.getSmtpPort()));
        }
        mailSender.setUsername(fromEmail);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");

        int port = config.getSmtpPort() != null ? Integer.parseInt(config.getSmtpPort()) : 587;
        if (port == 465 || (config.getSmtpSecure() != null && config.getSmtpSecure() && port != 587)) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.ssl.trust", config.getSmtpHost());
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text);

        helper.addAttachment(
                "invite.ics",
                new org.springframework.core.io.ByteArrayResource(icsContent.getBytes()),
                "text/calendar");

        mailSender.send(message);
    }
}
