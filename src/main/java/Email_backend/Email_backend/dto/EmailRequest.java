package Email_backend.Email_backend.dto;

import java.util.List;

public class EmailRequest {
    private String to;
    private String cc;
    private String subject;
    private String content;
    private List<AttachmentRequest> attachments;
    private Long draftUid;

    public EmailRequest() {}

    public Long getDraftUid() { return draftUid; }
    public void setDraftUid(Long draftUid) { this.draftUid = draftUid; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getCc() { return cc; }
    public void setCc(String cc) { this.cc = cc; }
    
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<AttachmentRequest> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentRequest> attachments) { this.attachments = attachments; }

    public static class AttachmentRequest {
        private String fileName;
        private String base64Content;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getBase64Content() { return base64Content; }
        public void setBase64Content(String base64Content) { this.base64Content = base64Content; }
    }
}
