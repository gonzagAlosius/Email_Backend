package Email_backend.Email_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

public class EmailResponse {
    private String sender;
    private String email;
    private String toName;
    private String toEmail;
    private String date;
    private String subject;
    private String snippet;
    private String content;
    private Long uid;
    
    @JsonProperty("isRead")
    private boolean isRead;

    private List<AttachmentResponse> attachments = new ArrayList<>();

    public EmailResponse() {}


    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getToName() { return toName; }
    public void setToName(String toName) { this.toName = toName; }

    public String getToEmail() { return toEmail; }
    public void setToEmail(String toEmail) { this.toEmail = toEmail; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getUid() { return uid; }
    public void setUid(Long uid) { this.uid = uid; }

    public boolean getIsRead() { return isRead; }
    public void setIsRead(boolean isRead) { this.isRead = isRead; }

    public List<AttachmentResponse> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentResponse> attachments) { this.attachments = attachments; }

    public static class AttachmentResponse {
        private String fileName;
        private String contentType;
        private String base64Data;

        public AttachmentResponse() {}

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }

        public String getBase64Data() { return base64Data; }
        public void setBase64Data(String base64Data) { this.base64Data = base64Data; }
    }
}
