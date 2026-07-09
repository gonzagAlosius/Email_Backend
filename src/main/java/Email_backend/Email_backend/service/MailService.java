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

    @org.springframework.beans.factory.annotation.Value("${email.default-sender:amit@testingmail.duckdns.org}")
    private String defaultSender;

    public void sendHtmlMail(String to, String subject, String html) throws Exception {
        String fromEmail = defaultSender;

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
        
        String actualFromEmail = fromEmail;
        String actualPassword = password;

        if (actualPassword == null) {
            // Fallback to default sender if password is not available
            actualFromEmail = defaultSender;
            Optional<UserEmailConfig> userConfigOpt = userEmailConfigRepository.findByEmailAddress(actualFromEmail);
            if (userConfigOpt.isPresent()) {
                actualPassword = encryptionService.decrypt(userConfigOpt.get().getEncryptedPassword());
            } else {
                System.err.println("Cannot send event invite: actualPassword is null and default config not found.");
                return;
            }
        }

        MailConfigDetector.Config config = orgEmailConfigService.getMailConfig(actualFromEmail, actualPassword);

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getSmtpHost());
        if (config.getSmtpPort() != null) {
            mailSender.setPort(Integer.parseInt(config.getSmtpPort()));
        }
        mailSender.setUsername(actualFromEmail);
        mailSender.setPassword(actualPassword);

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
        
        message.setFrom(new javax.mail.internet.InternetAddress(actualFromEmail));
        message.setRecipient(javax.mail.Message.RecipientType.TO, new javax.mail.internet.InternetAddress(to));
        message.setSubject(subject);

        // Multipart alternative for inline body and calendar rendering
        javax.mail.internet.MimeMultipart alternativeMultipart = new javax.mail.internet.MimeMultipart("alternative");

        // Body message part
        javax.mail.internet.MimeBodyPart textPart = new javax.mail.internet.MimeBodyPart();
        textPart.setContent(text, "text/html; charset=utf-8");
        alternativeMultipart.addBodyPart(textPart);

        // Calendar invitation inline part
        javax.mail.internet.MimeBodyPart calendarPart = new javax.mail.internet.MimeBodyPart();
        calendarPart.addHeader("Content-Class", "urn:content-classes:calendarmessage");
        calendarPart.setContent(icsContent, "text/calendar; method=REQUEST; charset=UTF-8");
        alternativeMultipart.addBodyPart(calendarPart);

        // Multipart mixed to contain both the inline rendering and the attachment
        javax.mail.internet.MimeMultipart mixedMultipart = new javax.mail.internet.MimeMultipart("mixed");
        
        javax.mail.internet.MimeBodyPart mainBodyPart = new javax.mail.internet.MimeBodyPart();
        mainBodyPart.setContent(alternativeMultipart);
        mixedMultipart.addBodyPart(mainBodyPart);

        // Attachment part
        javax.mail.internet.MimeBodyPart attachmentPart = new javax.mail.internet.MimeBodyPart();
        attachmentPart.setFileName("invite.ics");
        attachmentPart.setDisposition(javax.mail.Part.ATTACHMENT);
        attachmentPart.setDataHandler(new javax.activation.DataHandler(
                new javax.mail.util.ByteArrayDataSource(icsContent.getBytes("UTF-8"), "text/calendar; method=REQUEST; charset=UTF-8; name=\"invite.ics\"")
        ));
        mixedMultipart.addBodyPart(attachmentPart);

        message.setContent(mixedMultipart);

        mailSender.send(message);
    }
}
