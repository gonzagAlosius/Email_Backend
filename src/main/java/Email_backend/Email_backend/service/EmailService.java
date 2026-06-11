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

    @Autowired
    private OrgEmailConfigService orgEmailConfigService;

    public void sendEmail(EmailRequest emailRequest, String username, String password) {
        try {
            String resolvedPassword = MailConfigDetector.resolvePassword(username, password);
            MailConfigDetector.Config config = orgEmailConfigService.getMailConfig(username, resolvedPassword);

            org.springframework.mail.javamail.JavaMailSenderImpl mailSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
            mailSender.setHost(config.getSmtpHost());
            mailSender.setPort(Integer.parseInt(config.getSmtpPort()));
            mailSender.setUsername(username);
            mailSender.setPassword(resolvedPassword);

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");

            String smtpPort = config.getSmtpPort();
            String smtpHost = config.getSmtpHost();
            
            boolean useSsl = "465".equals(smtpPort);
            boolean useStartTls = "587".equals(smtpPort) || "25".equals(smtpPort) || 
                                  (smtpHost != null && (smtpHost.contains("outlook") || smtpHost.contains("office365")));

            if (!useSsl && !useStartTls) {
                if (config.getSmtpSecure() != null && config.getSmtpSecure()) {
                    useStartTls = true;
                }
            }

            if (useSsl) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.required", "true");
                props.put("mail.smtp.ssl.trust", smtpHost);
                props.put("mail.smtp.socketFactory.port", smtpPort);
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            } else if (useStartTls) {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.trust", smtpHost);
            } else {
                props.put("mail.smtp.starttls.enable", "false");
            }

            if (MailConfigDetector.isOAuthToken(resolvedPassword)) {
                props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
                props.put("mail.smtp.auth.login.disable", "true");
                props.put("mail.smtp.auth.plain.disable", "true");
            }

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
            try {
                saveToSentFolder(message, username, resolvedPassword, config);
            } catch (Exception imapEx) {
                System.err.println("Failed to save copy to Sent folder: " + imapEx.getMessage());
                imapEx.printStackTrace();
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }

    private void saveToSentFolder(MimeMessage message, String username, String password,
            MailConfigDetector.Config config) throws Exception {
        boolean isSecure = config.getImapSecure();
        String protocol = isSecure ? "imaps" : "imap";

        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", config.getImapHost());
        props.put("mail." + protocol + ".port", config.getImapPort());
        if (isSecure) {
            props.put("mail.imaps.ssl.trust", config.getImapHost());
        }

        if (MailConfigDetector.isOAuthToken(password)) {
            props.put("mail." + protocol + ".auth.mechanisms", "XOAUTH2");
            props.put("mail." + protocol + ".sasl.enable", "true");
        }

        Session session = Session.getInstance(props, null);
        Store store = session.getStore(protocol);
        store.connect(config.getImapHost(), username, password);

        Folder folder = store.getFolder(config.getSentFolder());

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

        folder.appendMessages(new Message[] { message });

        folder.close(false);
        store.close();
    }
}
