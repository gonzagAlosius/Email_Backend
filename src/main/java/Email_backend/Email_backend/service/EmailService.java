package Email_backend.Email_backend.service;

import Email_backend.Email_backend.dto.EmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ByteArrayResource;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;
import java.util.Base64;
import java.util.Properties;

@Service
public class EmailService {

    @Value("${spring.mail.host}")
    private String smtpHost;

    @Value("${spring.mail.port}")
    private String smtpPort;

    @Value("${mail.imap.host}")
    private String imapHost;

    @Value("${mail.imap.port}")
    private String imapPort;

    @Value("${mail.imap.sentfolder:Sent}")
    private String sentFolder;

    public void sendEmail(EmailRequest emailRequest, String username, String password) {
        try {
            org.springframework.mail.javamail.JavaMailSenderImpl mailSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
            mailSender.setHost(smtpHost);
            mailSender.setPort(Integer.parseInt(smtpPort));
            mailSender.setUsername(username);
            mailSender.setPassword(password);

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", smtpHost);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true); // true = multipart

            helper.setFrom(username);
            helper.setTo(emailRequest.getTo());
            
            if (emailRequest.getCc() != null && !emailRequest.getCc().isEmpty()) {
                helper.setCc(emailRequest.getCc());
            }
            
            helper.setSubject(emailRequest.getSubject());
            helper.setText(emailRequest.getContent());

            if (emailRequest.getAttachments() != null) {
                for (EmailRequest.AttachmentRequest att : emailRequest.getAttachments()) {
                    byte[] data = Base64.getDecoder().decode(att.getBase64Content());
                    helper.addAttachment(att.getFileName(), new ByteArrayResource(data));
                }
            }

            // 1. Send via SMTP
            mailSender.send(message);

            // 2. Save copy to IMAP Sent folder
            saveToSentFolder(message, username, password);

        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }

    private void saveToSentFolder(MimeMessage message, String username, String password) throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", imapPort);
        props.put("mail.imaps.ssl.trust", imapHost);

        Session session = Session.getInstance(props, null);
        Store store = session.getStore("imaps");
        store.connect(imapHost, username, password);

        Folder folder = store.getFolder(sentFolder);
        
        if (!folder.exists()) {
            // Check common names
            Folder sentMessages = store.getFolder("Sent Messages");
            if (sentMessages.exists()) {
                folder = sentMessages;
            } else {
                Folder inboxSent = store.getFolder("INBOX.Sent");
                if (inboxSent.exists()) {
                    folder = inboxSent;
                } else {
                    Folder sentItems = store.getFolder("Sent Items");
                    if (sentItems.exists()) {
                        folder = sentItems;
                    } else {
                        // Create it if it doesn't exist
                        folder.create(Folder.HOLDS_MESSAGES);
                    }
                }
            }
        }

        folder.open(Folder.READ_WRITE);
        
        // Ensure message changes are saved before appending
        message.saveChanges();
        message.setFlag(javax.mail.Flags.Flag.SEEN, true);
        
        folder.appendMessages(new Message[]{message});
        
        folder.close(false);
        store.close();
    }
}
