package Email_backend.Email_backend.service;

import Email_backend.Email_backend.dto.EmailResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import Email_backend.Email_backend.service.MailConfigDetector;

@Service
public class EmailReceiveService {

    @Autowired
    private OrgEmailConfigService orgEmailConfigService;

    public List<EmailResponse> fetchInbox(String username, String password, int page, int size) throws Exception {
        String resolvedPassword = MailConfigDetector.resolvePassword(username, password);
        MailConfigDetector.Config config = orgEmailConfigService.getMailConfig(username, resolvedPassword);
        return fetchEmailsFromFolder("INBOX", username, resolvedPassword, config, page, size);
    }

    public List<EmailResponse> fetchDraftMessages(String username, String password, int page, int size)
            throws Exception {
        String resolvedPassword = MailConfigDetector.resolvePassword(username, password);
        MailConfigDetector.Config config = orgEmailConfigService.getMailConfig(username, resolvedPassword);
        return fetchEmailsFromFolder("Drafts", username, resolvedPassword, config, page, size);
    }

    public List<EmailResponse> fetchSentMessages(String username, String password, int page, int size)
            throws Exception {
        String resolvedPassword = MailConfigDetector.resolvePassword(username, password);
        MailConfigDetector.Config config = orgEmailConfigService.getMailConfig(username, resolvedPassword);
        return fetchEmailsFromFolder(config.getSentFolder(), username, resolvedPassword, config, page, size);
    }

    private List<EmailResponse> fetchEmailsFromFolder(String folderName, String username, String password,
            MailConfigDetector.Config config, int page, int size) throws Exception {
        boolean isSecure = config.getImapSecure();
        String protocol = isSecure ? "imaps" : "imap";

        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", config.getImapHost());
        props.put("mail." + protocol + ".port", config.getImapPort());
        if (isSecure) {
            props.put("mail.imaps.ssl.trust", config.getImapHost()); // Fixes PKIX path building failed error
        }

        if (MailConfigDetector.isOAuthToken(password)) {
            props.put("mail." + protocol + ".auth.mechanisms", "XOAUTH2");
            props.put("mail." + protocol + ".auth.login.disable", "true");
            props.put("mail." + protocol + ".auth.plain.disable", "true");
        }

        Session session = Session.getInstance(props, null);
        session.setDebug(true);
        Store store = session.getStore(protocol);

        store.connect(config.getImapHost(), username, password);

        Folder folder = getFolderWithFallback(store, folderName, config.getImapHost());

        folder.open(Folder.READ_ONLY);

        Message[] messages = folder.getMessages();
        int count = messages.length;
        int start = Math.max(0, count - (page + 1) * size);
        int end = Math.max(0, count - page * size);

        // Optimize: Batch fetch envelopes, flags, and UIDs to avoid N+1 network queries
        if (count > 0 && start < end) {
            int numToFetch = end - start;
            Message[] messagesToFetch = new Message[numToFetch];
            for (int i = 0; i < numToFetch; i++) {
                messagesToFetch[i] = messages[start + i];
            }
            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(FetchProfile.Item.FLAGS);
            fp.add(UIDFolder.FetchProfileItem.UID);
            folder.fetch(messagesToFetch, fp);
        }

        List<EmailResponse> emailList = new ArrayList<>();

        for (int i = end - 1; i >= start; i--) {
            Message msg = messages[i];
            EmailResponse dto = new EmailResponse();

            Address[] froms = msg.getFrom();
            if (froms != null && froms.length > 0) {
                String fromAuth = froms[0].toString();
                if (fromAuth.contains("<")) {
                    dto.setSender(fromAuth.substring(0, fromAuth.indexOf("<")).trim().replace("\"", ""));
                    dto.setEmail(fromAuth.substring(fromAuth.indexOf("<") + 1, fromAuth.indexOf(">")));
                } else {
                    dto.setSender(fromAuth);
                    dto.setEmail(fromAuth);
                }
            } else {
                dto.setSender("Unknown");
                dto.setEmail("unknown@email.com");
            }

            Address[] recipients = msg.getRecipients(Message.RecipientType.TO);
            if (recipients != null && recipients.length > 0) {
                String rec = recipients[0].toString();
                if (rec.contains("<")) {
                    String displayName = rec.substring(0, rec.indexOf("<")).trim().replace("\"", "");
                    String emailAddress = rec.substring(rec.indexOf("<") + 1, rec.indexOf(">"));
                    dto.setToName(displayName.isEmpty() ? emailAddress : displayName);
                    dto.setToEmail(emailAddress);
                } else {
                    dto.setToName(rec);
                    dto.setToEmail(rec);
                }
            } else {
                dto.setToName("Unknown Recipient");
                dto.setToEmail("");
            }

            if (folder instanceof com.sun.mail.imap.IMAPFolder) {
                try {
                    dto.setUid(((com.sun.mail.imap.IMAPFolder) folder).getUID(msg));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            dto.setSubject(msg.getSubject() != null ? msg.getSubject() : "(No Subject)");
            dto.setDate(msg.getSentDate() != null ? msg.getSentDate().toString() : "");

            // Eagerly load content and attachments only for the latest 5 messages on page 0
            if (page == 0 && end - 1 - i < 5) {
                StringBuilder htmlBuilder = new StringBuilder();
                StringBuilder textBuilder = new StringBuilder();
                List<EmailResponse.AttachmentResponse> attachments = new ArrayList<>();
                try {
                    extractContentAndAttachments(msg, htmlBuilder, textBuilder, attachments);
                } catch (Exception e) {
                    textBuilder.append("[Content unavailable due to parsing error: ").append(e.getMessage())
                            .append("]");
                }

                String html = htmlBuilder.toString().trim();
                String text = textBuilder.toString().trim();
                String content = html.isEmpty() ? text : html;

                dto.setContent(content);
                dto.setAttachments(attachments);

                String snippetSource = text.isEmpty() ? htmlToText(html) : text;
                String snippetRaw = snippetSource.replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ").trim();
                dto.setSnippet(snippetRaw.length() > 60 ? snippetRaw.substring(0, 60) + "..." : snippetRaw);
            } else {
                dto.setContent("");
                dto.setSnippet("Tap to load message details...");
                dto.setAttachments(new ArrayList<>());
            }

            dto.setIsRead(msg.isSet(Flags.Flag.SEEN));

            emailList.add(dto);
        }

        folder.close(false);
        store.close();

        return emailList;
    }

    public EmailResponse fetchEmailDetailsByUid(String folderName, long uid, String username, String password)
            throws Exception {
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
        session.setDebug(true);
        Store store = session.getStore("imaps");
        store.connect(config.getImapHost(), username, resolvedPassword);

        Folder folder = getFolderWithFallback(store, folderName, config.getImapHost());
        try {
            folder.open(Folder.READ_WRITE);
            System.out.println("DEBUG: IMAP folder " + folderName + " opened in READ_WRITE mode");
        } catch (MessagingException e) {
            System.err.println(
                    "WARNING: Failed to open IMAP folder " + folderName + " in READ_WRITE mode: " + e.getMessage());
            e.printStackTrace();
            folder.open(Folder.READ_ONLY);
        }

        EmailResponse dto = new EmailResponse();
        if (folder instanceof com.sun.mail.imap.IMAPFolder) {
            com.sun.mail.imap.IMAPFolder imapFolder = (com.sun.mail.imap.IMAPFolder) folder;
            Message msg = imapFolder.getMessageByUID(uid);
            if (msg != null) {
                if (folder.getMode() == Folder.READ_WRITE) {
                    try {
                        msg.setFlag(Flags.Flag.SEEN, true);
                        System.out.println("DEBUG: Successfully marked message UID " + uid + " as SEEN");
                    } catch (Exception ex) {
                        System.err.println("Could not mark message as SEEN: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                } else {
                    System.err.println(
                            "WARNING: Cannot set SEEN flag because folder " + folderName + " is in READ_ONLY mode");
                }
                dto.setUid(uid);
                Address[] froms = msg.getFrom();
                if (froms != null && froms.length > 0) {
                    String fromAuth = froms[0].toString();
                    if (fromAuth.contains("<")) {
                        dto.setSender(fromAuth.substring(0, fromAuth.indexOf("<")).trim().replace("\"", ""));
                        dto.setEmail(fromAuth.substring(fromAuth.indexOf("<") + 1, fromAuth.indexOf(">")));
                    } else {
                        dto.setSender(fromAuth);
                        dto.setEmail(fromAuth);
                    }
                } else {
                    dto.setSender("Unknown");
                    dto.setEmail("unknown@email.com");
                }

                Address[] recipients = msg.getRecipients(Message.RecipientType.TO);
                if (recipients != null && recipients.length > 0) {
                    String rec = recipients[0].toString();
                    if (rec.contains("<")) {
                        String displayName = rec.substring(0, rec.indexOf("<")).trim().replace("\"", "");
                        String emailAddress = rec.substring(rec.indexOf("<") + 1, rec.indexOf(">"));
                        dto.setToName(displayName.isEmpty() ? emailAddress : displayName);
                        dto.setToEmail(emailAddress);
                    } else {
                        dto.setToName(rec);
                        dto.setToEmail(rec);
                    }
                } else {
                    dto.setToName("Unknown Recipient");
                    dto.setToEmail("");
                }

                dto.setSubject(msg.getSubject() != null ? msg.getSubject() : "(No Subject)");
                dto.setDate(msg.getSentDate() != null ? msg.getSentDate().toString() : "");
                dto.setIsRead(msg.isSet(Flags.Flag.SEEN));

                StringBuilder htmlBuilder = new StringBuilder();
                StringBuilder textBuilder = new StringBuilder();
                List<EmailResponse.AttachmentResponse> attachments = new ArrayList<>();
                try {
                    extractContentAndAttachments(msg, htmlBuilder, textBuilder, attachments);
                } catch (Exception e) {
                    textBuilder.append("[Content unavailable due to parsing error: ").append(e.getMessage())
                            .append("]");
                }
                String html = htmlBuilder.toString().trim();
                String text = textBuilder.toString().trim();
                dto.setContent(html.isEmpty() ? text : html);
                dto.setAttachments(attachments);
            }
        }

        folder.close(false);
        store.close();
        return dto;
    }

    private String htmlToText(String html) {
        if (html == null) {
            return "";
        }

        // 1. Remove style, script and head tags and their contents (DOTALL enabled with
        // ?s)
        html = html.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        html = html.replaceAll("(?is)<head[^>]*>.*?</head>", "");

        // 2. Replace block tags and breaks with newlines
        html = html.replaceAll("(?i)<br\\s*/?>", "\n");
        html = html.replaceAll("(?i)</p>", "\n\n");
        html = html.replaceAll("(?i)</div>", "\n");
        html = html.replaceAll("(?i)</td>", "\t");
        html = html.replaceAll("(?i)</tr>", "\n");
        html = html.replaceAll("(?i)<li>", "\n • ");

        // 3. Strip all remaining HTML tags
        html = html.replaceAll("<[^>]*>", "");

        // 4. Decode HTML entities
        html = html.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'");

        // 5. Clean up excessive consecutive newlines (more than 2 newlines in a row ->
        // 2 newlines)
        html = html.replaceAll("\n{3,}", "\n\n");

        return html.trim();
    }

    private void extractContentAndAttachments(Part part, StringBuilder htmlBuilder, StringBuilder textBuilder,
            List<EmailResponse.AttachmentResponse> attachmentsList) throws Exception {
        if (part.isMimeType("text/plain") && part.getFileName() == null) {
            textBuilder.append(part.getContent().toString());
        } else if (part.isMimeType("text/html") && part.getFileName() == null) {
            htmlBuilder.append(part.getContent().toString());
        } else if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++) {
                extractContentAndAttachments(mp.getBodyPart(i), htmlBuilder, textBuilder, attachmentsList);
            }
        } else if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null) {
            EmailResponse.AttachmentResponse att = new EmailResponse.AttachmentResponse();
            att.setFileName(part.getFileName());

            String fullContentType = part.getContentType();
            String cleanContentType = fullContentType != null ? fullContentType.split(";")[0].trim()
                    : "application/octet-stream";
            att.setContentType(cleanContentType);
            att.setBase64Data(""); // Eager downloading removed for instant load speed

            attachmentsList.add(att);
        }
    }

    public byte[] fetchAttachmentBytes(String folderName, long uid, String fileName, String username, String password)
            throws Exception {
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

        Folder folder = getFolderWithFallback(store, folderName, config.getImapHost());
        folder.open(Folder.READ_ONLY);

        byte[] bytes = null;
        if (folder instanceof com.sun.mail.imap.IMAPFolder) {
            com.sun.mail.imap.IMAPFolder imapFolder = (com.sun.mail.imap.IMAPFolder) folder;
            Message msg = imapFolder.getMessageByUID(uid);
            if (msg != null) {
                bytes = findAttachmentBytes(msg, fileName);
            }
        }

        folder.close(false);
        store.close();
        return bytes;
    }

    private byte[] findAttachmentBytes(Part part, String fileName) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++) {
                byte[] bytes = findAttachmentBytes(mp.getBodyPart(i), fileName);
                if (bytes != null) {
                    return bytes;
                }
            }
        } else if (fileName.equalsIgnoreCase(part.getFileName())) {
            try (InputStream is = part.getInputStream();
                    ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                return os.toByteArray();
            }
        }
        return null;
    }

    private List<EmailResponse> getMockEmails(String folderName, String username) {
        List<EmailResponse> list = new ArrayList<>();
        if (folderName.equalsIgnoreCase("INBOX")) {
            // Email 1
            EmailResponse email1 = new EmailResponse();
            email1.setSender("Google Security Team");
            email1.setEmail("no-reply@accounts.google.com");
            email1.setToName(username);
            email1.setToEmail(username);
            email1.setDate(new java.util.Date().toString());
            email1.setSubject("Security Alert: Standard Password Authentication Allowed");
            email1.setSnippet("Standard password authentication was bypassed successfully for testing.");
            email1.setContent("<div style='font-family: Arial, sans-serif; padding: 20px; color: #333;'>"
                    + "<h2>Security Alert</h2>"
                    + "<p>Dear User,</p>"
                    + "<p>This is a simulated verification email confirming that <strong>Standard Password Authentication</strong> "
                    + "for Gmail bypass was activated successfully on your testing device.</p>"
                    + "<p>If this was you, no action is needed. Your email sandbox is fully ready for development testing.</p>"
                    + "<br/><p>Best regards,<br/>Google Security Team (Mock)</p></div>");
            email1.setUid(1001L);
            email1.setIsRead(false);
            list.add(email1);

            // Email 2
            EmailResponse email2 = new EmailResponse();
            email2.setSender("Financial Analyst");
            email2.setEmail("analyst@botsuat.com");
            email2.setToName(username);
            email2.setToEmail(username);
            email2.setDate(new java.util.Date(System.currentTimeMillis() - 3600000).toString());
            email2.setSubject("Q2 Financial Statement & Invoices");
            email2.setSnippet("Please find attached the Q2 financial statement summary for review.");
            email2.setContent("<div style='font-family: Arial, sans-serif; padding: 20px; color: #333;'>"
                    + "<h2>Q2 Financial Report</h2>"
                    + "<p>Hi Team,</p>"
                    + "<p>Attached is the Q2 budget sheet along with pending invoice details. Let me know if you see any deviations.</p>"
                    + "<ul>"
                    + "  <li>Total Revenue: $145,000</li>"
                    + "  <li>Net Income: $32,400</li>"
                    + "  <li>Operating Margin: 22.3%</li>"
                    + "</ul>"
                    + "<p>Thanks,<br/>Finance Department</p></div>");
            email2.setUid(1002L);
            email2.setIsRead(true);

            EmailResponse.AttachmentResponse att = new EmailResponse.AttachmentResponse();
            att.setFileName("Q2_Statement.pdf");
            att.setContentType("application/pdf");
            att.setBase64Data("");
            email2.getAttachments().add(att);
            list.add(email2);

            // Email 3
            EmailResponse email3 = new EmailResponse();
            email3.setSender("Microsoft Office 365");
            email3.setEmail("billing@microsoft.com");
            email3.setToName(username);
            email3.setToEmail(username);
            email3.setDate(new java.util.Date(System.currentTimeMillis() - 7200000).toString());
            email3.setSubject("Microsoft Cloud Subscription Invoice");
            email3.setSnippet("Your subscription renewal invoice for next month is now ready.");
            email3.setContent("<div style='font-family: Arial, sans-serif; padding: 20px; color: #333;'>"
                    + "<h2>Subscription Invoice</h2>"
                    + "<p>Dear Customer,</p>"
                    + "<p>Your monthly billing invoice for Microsoft Office 365 cloud hosting services has been processed.</p>"
                    + "<p><strong>Amount Billed:</strong> $120.00 USD<br/><strong>Due Date:</strong> June 25, 2026</p>"
                    + "<p>Thank you for choosing Microsoft.</p></div>");
            email3.setUid(1003L);
            email3.setIsRead(true);
            list.add(email3);

        } else {
            // Sent folder
            EmailResponse email1 = new EmailResponse();
            email1.setSender(username);
            email1.setEmail(username);
            email1.setToName("client@example.com");
            email1.setToEmail("client@example.com");
            email1.setDate(new java.util.Date().toString());
            email1.setSubject("Re: Budget Approval Proposal");
            email1.setSnippet("Here is the requested proposal document for your team's signature.");
            email1.setContent("<div style='font-family: Arial, sans-serif; padding: 20px; color: #333;'>"
                    + "<p>Hi Client,</p>"
                    + "<p>I have attached the updated proposal. Please let us know if we can proceed with this layout.</p>"
                    + "<p>Regards,<br/>Sender</p></div>");
            email1.setUid(2001L);
            email1.setIsRead(true);
            list.add(email1);
        }
        return list;
    }

    private EmailResponse getMockEmailDetails(String folderName, long uid, String username) {
        List<EmailResponse> mocks = getMockEmails(folderName, username);
        for (EmailResponse item : mocks) {
            if (item.getUid() != null && item.getUid() == uid) {
                return item;
            }
        }
        EmailResponse fallback = new EmailResponse();
        fallback.setUid(uid);
        fallback.setSubject("Mock Email Content");
        fallback.setContent("<div><p>This is fallback mock content for mail ID " + uid + ".</p></div>");
        return fallback;
    }

    public static Folder getFolderWithFallback(Store store, String folderName, String imapHost) throws Exception {
        Folder folder = store.getFolder(folderName);
        if (folder.exists()) {
            return folder;
        }

        // Try standard fallback names
        String cleanName = folderName.toLowerCase();
        String[] fallbacks = new String[0];
        if (cleanName.contains("draft")) {
            fallbacks = new String[] { "Drafts", "Draft", "INBOX.Drafts", "INBOX.Draft", "Drafts Items", "Draft Items" };
        } else if (cleanName.contains("sent")) {
            fallbacks = new String[] { "Sent", "Sent Messages", "Sent Items", "INBOX.Sent", "INBOX.Sent Items" };
        }

        for (String fb : fallbacks) {
            Folder f = store.getFolder(fb);
            if (f.exists()) {
                return f;
            }
            if (imapHost != null && (imapHost.contains("gmail.com") || imapHost.contains("googlemail.com"))) {
                f = store.getFolder("[Gmail]/" + fb);
                if (f.exists()) return f;
            }
        }
        return folder;
    }
}
