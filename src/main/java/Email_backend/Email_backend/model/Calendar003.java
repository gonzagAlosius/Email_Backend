package Email_backend.Email_backend.model;

import java.time.LocalDateTime;

public class Calendar003 {
    private Integer orgcode;
    private Integer calid;
    private Integer eventId;
    private String email;
    private String displayName;
    private String responseStatus;
    private LocalDateTime restimestamp;
    private Boolean isOptional;

    // Getters and Setters
    public Integer getOrgcode() { return orgcode; }
    public void setOrgcode(Integer orgcode) { this.orgcode = orgcode; }

    public Integer getCalid() { return calid; }
    public void setCalid(Integer calid) { this.calid = calid; }

    public Integer getEventId() { return eventId; }
    public void setEventId(Integer eventId) { this.eventId = eventId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getResponseStatus() { return responseStatus; }
    public void setResponseStatus(String responseStatus) { this.responseStatus = responseStatus; }

    public LocalDateTime getRestimestamp() { return restimestamp; }
    public void setRestimestamp(LocalDateTime restimestamp) { this.restimestamp = restimestamp; }

    public Boolean getIsOptional() { return isOptional; }
    public void setIsOptional(Boolean isOptional) { this.isOptional = isOptional; }
}
