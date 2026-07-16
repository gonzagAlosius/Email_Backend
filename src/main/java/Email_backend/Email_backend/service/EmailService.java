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
import javax.mail.internet.InternetAddress;
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
            if (emailRequest.getTo() != null && !emailRequest.getTo().isEmpty()) {
                helper.setTo(javax.mail.internet.InternetAddress.parse(emailRequest.getTo(), false));
            }

            if (emailRequest.getCc() != null && !emailRequest.getCc().isEmpty()) {
                helper.setCc(javax.mail.internet.InternetAddress.parse(emailRequest.getCc(), false));
            }

            helper.setSubject(emailRequest.getSubject());
            helper.setText(emailRequest.getContent(), true); // true = HTML

            if (emailRequest.getInReplyTo() != null && !emailRequest.getInReplyTo().isEmpty()) {
                message.setHeader("In-Reply-To", emailRequest.getInReplyTo());
            }
            if (emailRequest.getReferences() != null && !emailRequest.getReferences().isEmpty()) {
                message.setHeader("References", emailRequest.getReferences());
            }

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
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", config.getImapHost());
        props.put("mail.imaps.port", config.getImapPort());
        props.put("mail.imaps.ssl.trust", config.getImapHost());

        if (MailConfigDetector.isOAuthToken(password)) {
            props.put("mail.imaps.auth.mechanisms", "XOAUTH2");
            props.put("mail.imaps.auth.login.disable", "true");
            props.put("mail.imaps.auth.plain.disable", "true");
        }

        Session session = Session.getInstance(props, null);
        Store store = session.getStore("imaps");
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
                    if (config.getImapHost().contains("gmail.com")) {
                        folder = store.getFolder("[Gmail]/Sent Mail");
                    }
                    if (!folder.exists()) {
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

    public Long saveDraft(EmailRequest emailRequest, String username, String password) throws Exception {
        String resolvedPassword = MailConfigDetector.resolvePassword(username, password);
        MailConfigDetector.Config config = orgEmailConfigService.getMailConfig(username, resolvedPassword);

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", config.getImapHost());
        props.put("mail.imaps.port", config.getImapPort());
        props.put("mail.imaps.ssl.trust", config.getImapHost());

        if (MailConfigDetector.isOAuthToken(resolvedPassword)) {
            props.put("mail.imaps.auth.mechanisms", "XOAUTH2");
            props.put("mail.imaps.auth.login.disable", "true");
            props.put("mail.imaps.auth.plain.disable", "true");
        }

        Session session = Session.getInstance(props, null);
        Store store = session.getStore("imaps");
        store.connect(config.getImapHost(), username, resolvedPassword);

        String imapHost = config.getImapHost();
        Folder folder = getDraftsFolderWithFallback(store, imapHost);

        if (!folder.exists()) {
            folder.create(Folder.HOLDS_MESSAGES);
        }

        folder.open(Folder.READ_WRITE);

        // If we are updating an existing draft, delete the old one first
        if (emailRequest.getDraftUid() != null) {
            if (folder instanceof com.sun.mail.imap.IMAPFolder) {
                com.sun.mail.imap.IMAPFolder imapFolder = (com.sun.mail.imap.IMAPFolder) folder;
                Message oldMsg = imapFolder.getMessageByUID(emailRequest.getDraftUid());
                if (oldMsg != null) {
                    oldMsg.setFlag(javax.mail.Flags.Flag.DELETED, true);
                }
            }
        }

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username));
        if (emailRequest.getTo() != null && !emailRequest.getTo().trim().isEmpty()) {
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailRequest.getTo()));
        }
        if (emailRequest.getCc() != null && !emailRequest.getCc().trim().isEmpty()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(emailRequest.getCc()));
        }
        message.setSubject(emailRequest.getSubject() != null ? emailRequest.getSubject() : "");
        message.setContent(emailRequest.getContent() != null ? emailRequest.getContent() : "", "text/html; charset=utf-8");
        
        if (emailRequest.getInReplyTo() != null && !emailRequest.getInReplyTo().isEmpty()) {
            message.setHeader("In-Reply-To", emailRequest.getInReplyTo());
        }
        if (emailRequest.getReferences() != null && !emailRequest.getReferences().isEmpty()) {
            message.setHeader("References", emailRequest.getReferences());
        }
        
        message.setFlag(javax.mail.Flags.Flag.DRAFT, true);
        message.saveChanges();

        Long newUid = null;
        if (folder instanceof com.sun.mail.imap.IMAPFolder) {
            try {
                com.sun.mail.imap.AppendUID[] uids = ((com.sun.mail.imap.IMAPFolder) folder).appendUIDMessages(new Message[] { message });
                if (uids != null && uids.length > 0) {
                    newUid = uids[0].uid;
                }
            } catch (Exception e) {
                System.err.println("Failed to append via appendUIDMessages: " + e.getMessage());
                folder.appendMessages(new Message[] { message });
            }
        } else {
            folder.appendMessages(new Message[] { message });
        }

        folder.close(true); // expunge=true to delete old draft marked as DELETED
        store.close();

        return newUid;
    }

    public void deleteDraft(Long uid, String username, String password) throws Exception {
        String resolvedPassword = MailConfigDetector.resolvePassword(username, password);
        MailConfigDetector.Config config = orgEmailConfigService.getMailConfig(username, resolvedPassword);

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", config.getImapHost());
        props.put("mail.imaps.port", config.getImapPort());
        props.put("mail.imaps.ssl.trust", config.getImapHost());

        if (MailConfigDetector.isOAuthToken(resolvedPassword)) {
            props.put("mail.imaps.auth.mechanisms", "XOAUTH2");
            props.put("mail.imaps.auth.login.disable", "true");
            props.put("mail.imaps.auth.plain.disable", "true");
        }

        Session session = Session.getInstance(props, null);
        Store store = session.getStore("imaps");
        store.connect(config.getImapHost(), username, resolvedPassword);

        String imapHost = config.getImapHost();
        Folder folder = getDraftsFolderWithFallback(store, imapHost);

        if (folder.exists()) {
            folder.open(Folder.READ_WRITE);
            if (folder instanceof com.sun.mail.imap.IMAPFolder) {
                com.sun.mail.imap.IMAPFolder imapFolder = (com.sun.mail.imap.IMAPFolder) folder;
                Message msg = imapFolder.getMessageByUID(uid);
                if (msg != null) {
                    msg.setFlag(javax.mail.Flags.Flag.DELETED, true);
                }
            }
            folder.close(true);
        }
        store.close();
    }

    private Folder getDraftsFolderWithFallback(Store store, String imapHost) throws Exception {
        String primaryName = "Drafts";
        if (imapHost != null && (imapHost.contains("gmail.com") || imapHost.contains("googlemail.com"))) {
            primaryName = "[Gmail]/Drafts";
        }
        Folder folder = store.getFolder(primaryName);
        if (folder.exists()) {
            return folder;
        }

        String[] fallbacks = { "Drafts", "Draft", "INBOX.Drafts", "INBOX.Draft", "Drafts Items", "Draft Items" };
        for (String fb : fallbacks) {
            Folder f = store.getFolder(fb);
            if (f.exists()) {
                return f;
            }
            if (imapHost != null && (imapHost.contains("gmail.com") || imapHost.contains("googlemail.com"))) {
                f = store.getFolder("[Gmail]/" + fb);
                if (f.exists()) {
                    return f;
                }
            }
        }
        return folder;
    }
}
