package Email_backend.Email_backend.service;

import Email_backend.Email_backend.dto.EmailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
public class EmailReceiveService {

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    public List<EmailResponse> fetchInbox() throws Exception {
        return fetchEmailsFromFolder("INBOX");
    }

    public List<EmailResponse> fetchSentMessages() throws Exception {
        // Gmail usually names the sent folder "[Gmail]/Sent Mail"
        return fetchEmailsFromFolder("[Gmail]/Sent Mail");
    }

    private List<EmailResponse> fetchEmailsFromFolder(String folderName) throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", "imap.gmail.com");
        props.put("mail.imaps.port", "993");

        Session session = Session.getInstance(props, null);
        Store store = session.getStore("imaps");
        
        store.connect(username, password.replace(" ", ""));

        Folder folder = store.getFolder(folderName);
        if (!folder.exists()) {
            // Fallback for non-Gmail or localized folders
            if (folderName.contains("Sent")) {
                folder = store.getFolder("Sent");
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
            
            dto.setSubject(msg.getSubject() != null ? msg.getSubject() : "(No Subject)");
            dto.setDate(msg.getSentDate() != null ? msg.getSentDate().toString() : "");
            
            String content = getTextFromMessage(msg);
            dto.setContent(content);
            String snippetRaw = content.replace("\n", " ").replace("\r", "");
            dto.setSnippet(snippetRaw.length() > 60 ? snippetRaw.substring(0, 60) + "..." : snippetRaw);
            
            dto.setIsRead(msg.isSet(Flags.Flag.SEEN));
            
            emailList.add(dto);
        }

        folder.close(false);
        store.close();

        return emailList;
    }

    private String getTextFromMessage(Message message) throws Exception {
        try {
            if (message.isMimeType("text/plain")) {
                return message.getContent().toString();
            } else if (message.isMimeType("multipart/*")) {
                MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
                return getTextFromMimeMultipart(mimeMultipart);
            }
        } catch (Exception e) {
            return "[Content unavailable]";
        }
        return "";
    }

    private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws Exception {
        StringBuilder result = new StringBuilder();
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.getContent());
                break; 
            } else if (bodyPart.isMimeType("text/html")) {
                String html = (String) bodyPart.getContent();
                result.append(html.replaceAll("<[^>]*>", ""));
            } else if (bodyPart.getContent() instanceof MimeMultipart){
                result.append(getTextFromMimeMultipart((MimeMultipart)bodyPart.getContent()));
            }
        }
        return result.toString().trim();
    }
}
