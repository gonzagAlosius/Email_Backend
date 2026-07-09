package Email_backend.Email_backend.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Calendar002 {
    private Integer orgcode;
    private Integer calid;
    private Integer eventid;
    private Integer organizerId;
    private String title;
    private String description;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer isAllDay;
    private Integer isRecurring;
    private String recurrenceRule;
    private String meeturl;
    private LocalDate enddate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Getters and Setters
    public Integer getOrgcode() { return orgcode; }
    public void setOrgcode(Integer orgcode) { this.orgcode = orgcode; }

    public Integer getCalid() { return calid; }
    public void setCalid(Integer calid) { this.calid = calid; }

    public Integer getEventid() { return eventid; }
    public void setEventid(Integer eventid) { this.eventid = eventid; }

    public Integer getOrganizerId() { return organizerId; }
    public void setOrganizerId(Integer organizerId) { this.organizerId = organizerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getIsAllDay() { return isAllDay; }
    public void setIsAllDay(Integer isAllDay) { this.isAllDay = isAllDay; }

    public Integer getIsRecurring() { return isRecurring; }
    public void setIsRecurring(Integer isRecurring) { this.isRecurring = isRecurring; }

    public String getRecurrenceRule() { return recurrenceRule; }
    public void setRecurrenceRule(String recurrenceRule) { this.recurrenceRule = recurrenceRule; }

    public String getMeeturl() { return meeturl; }
    public void setMeeturl(String meeturl) { this.meeturl = meeturl; }

    public LocalDate getEnddate() { return enddate; }
    public void setEnddate(LocalDate enddate) { this.enddate = enddate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
