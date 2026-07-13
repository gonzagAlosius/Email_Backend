package Email_backend.Email_backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public class EventRequest {

    private String title;
    private String description;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<String> attendees;
    private List<String> optionalAttendees;
    private boolean isAllDay;
    private boolean isTeamsMeeting;
    private String agenda;
    private String categories;
    private String reminder;
    private String sensitivity;
    private String importance;
    private String timeZone;
    private String showAs;
    private String recurrence;
    private Integer calid;
    private Integer orgcode;
    private String meeturl;

    // Getters and Setters
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
    public List<String> getAttendees() { return attendees; }
    public void setAttendees(List<String> attendees) { this.attendees = attendees; }
    public String getRecurrence() { return recurrence; }
    public void setRecurrence(String recurrence) { this.recurrence = recurrence; }
    
    public List<String> getOptionalAttendees() { return optionalAttendees; }
    public void setOptionalAttendees(List<String> optionalAttendees) { this.optionalAttendees = optionalAttendees; }
    
    public boolean isAllDay() { return isAllDay; }
    public void setAllDay(boolean allDay) { isAllDay = allDay; }
    
    public boolean isTeamsMeeting() { return isTeamsMeeting; }
    public void setTeamsMeeting(boolean teamsMeeting) { isTeamsMeeting = teamsMeeting; }
    
    public String getAgenda() { return agenda; }
    public void setAgenda(String agenda) { this.agenda = agenda; }
    
    public String getCategories() { return categories; }
    public void setCategories(String categories) { this.categories = categories; }
    
    public String getReminder() { return reminder; }
    public void setReminder(String reminder) { this.reminder = reminder; }
    
    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
    
    public String getImportance() { return importance; }
    public void setImportance(String importance) { this.importance = importance; }
    
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    
    public String getShowAs() { return showAs; }
    public void setShowAs(String showAs) { this.showAs = showAs; }
    
    public Integer getCalid() { return calid; }
    public void setCalid(Integer calid) { this.calid = calid; }
    
    public Integer getOrgcode() { return orgcode; }
    public void setOrgcode(Integer orgcode) { this.orgcode = orgcode; }
    
    public String getMeeturl() { return meeturl; }
    public void setMeeturl(String meeturl) { this.meeturl = meeturl; }
}
