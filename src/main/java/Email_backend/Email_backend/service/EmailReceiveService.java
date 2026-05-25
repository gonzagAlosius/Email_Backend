package Email_backend.Email_backend.service;

import Email_backend.Email_backend.dto.EmailResponse;
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

@Service
public class EmailReceiveService {

    @Value("${mail.imap.host}")
    private String imapHost;

    @Value("${mail.imap.port}")
    private String imapPort;

    @Value("${mail.imap.sentfolder:Sent}")
    private String sentFolder;

    public List<EmailResponse> fetchInbox(String username, String password) throws Exception {
        return fetchEmailsFromFolder("INBOX", username, password);
    }

    public List<EmailResponse> fetchSentMessages(String username, String password) throws Exception {
        return fetchEmailsFromFolder(sentFolder, username, password);
    }

    private List<EmailResponse> fetchEmailsFromFolder(String folderName, String username, String password) throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", imapPort);
        props.put("mail.imaps.ssl.trust", imapHost); // Fixes PKIX path building failed error

        Session session = Session.getInstance(props, null);
        Store store = session.getStore("imaps");
        
        store.connect(imapHost, username, password);

        Folder folder = store.getFolder(folderName);
        if (!folder.exists()) {
            // Fallback for different folder naming conventions
            if (folderName.equalsIgnoreCase("Sent")) {
                folder = store.getFolder("Sent Messages");
                if (!folder.exists()) {
                     folder = store.getFolder("INBOX.Sent");
                }
            }
        }
        
        folder.open(Folder.READ_ONLY);

        Message[] messages = folder.getMessages();
        int count = messages.length;
        int limit = 15; 
        int start = Math.max(0, count - limit);

        List<EmailResponse> emailList = new ArrayList<>();
        
        for (int i = count - 1; i >= start; i--) {
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
            
            dto.setSubject(msg.getSubject() != null ? msg.getSubject() : "(No Subject)");
            dto.setDate(msg.getSentDate() != null ? msg.getSentDate().toString() : "");
            
            StringBuilder contentBuilder = new StringBuilder();
            List<EmailResponse.AttachmentResponse> attachments = new ArrayList<>();
            try {
                extractContentAndAttachments(msg, contentBuilder, attachments);
            } catch (Exception e) {
                contentBuilder.append("[Content unavailable due to parsing error: ").append(e.getMessage()).append("]");
            }
            
            String content = contentBuilder.toString().trim();
            dto.setContent(content);
            dto.setAttachments(attachments);
            String snippetRaw = content.replace("\n", " ").replace("\r", "");
            dto.setSnippet(snippetRaw.length() > 60 ? snippetRaw.substring(0, 60) + "..." : snippetRaw);
            
            dto.setIsRead(msg.isSet(Flags.Flag.SEEN));
            
            emailList.add(dto);
        }

        folder.close(false);
        store.close();

        return emailList;
    }

    private String htmlToText(String html) {
        if (html == null) {
            return "";
        }
        
        // 1. Remove style, script and head tags and their contents (DOTALL enabled with ?s)
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
        
        // 5. Clean up excessive consecutive newlines (more than 2 newlines in a row -> 2 newlines)
        html = html.replaceAll("\n{3,}", "\n\n");
        
        return html.trim();
    }

    private void extractContentAndAttachments(Part part, StringBuilder bodyBuilder, List<EmailResponse.AttachmentResponse> attachmentsList) throws Exception {
        if (part.isMimeType("text/plain") && part.getFileName() == null) {
            bodyBuilder.append(part.getContent().toString());
        } else if (part.isMimeType("text/html") && part.getFileName() == null) {
            bodyBuilder.append(htmlToText(part.getContent().toString()));
        } else if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++) {
                extractContentAndAttachments(mp.getBodyPart(i), bodyBuilder, attachmentsList);
            }
        } else if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null) {
            EmailResponse.AttachmentResponse att = new EmailResponse.AttachmentResponse();
            att.setFileName(part.getFileName());
            
            String fullContentType = part.getContentType();
            String cleanContentType = fullContentType != null ? fullContentType.split(";")[0].trim() : "application/octet-stream";
            att.setContentType(cleanContentType);
            
            try (InputStream is = part.getInputStream();
                 ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                String base64Data = Base64.getEncoder().encodeToString(os.toByteArray());
                att.setBase64Data(base64Data);
            }
            
            attachmentsList.add(att);
        }
    }
}
