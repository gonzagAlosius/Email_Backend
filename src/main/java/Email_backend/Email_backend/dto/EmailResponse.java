package Email_backend.Email_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EmailResponse {
    private String sender;
    private String email;
    private String date;
    private String subject;
    private String snippet;
    private String content;
    
    @JsonProperty("isRead")
    private boolean isRead;

    public EmailResponse() {}

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public boolean getIsRead() { return isRead; }
    public void setIsRead(boolean isRead) { this.isRead = isRead; }
}
