package Email_backend.Email_backend.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    
    @Column(length = 2000)
    private String description;
    
    private String location;
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    private String recurrence;
    
    @Column(name = "organizer_email")
    private String organizerEmail;
    
    @Column(name = "is_all_day")
    private boolean isAllDay;
    
    @Column(name = "is_teams_meeting")
    private boolean isTeamsMeeting;
    
    @Column(length = 5000)
    private String agenda;
    
    private String categories;
    private String reminder;
    private String sensitivity;
    private String importance;
    
    @Column(name = "time_zone")
    private String timeZone;
    
    @Column(name = "show_as")
    private String showAs;
    
    @Column(name = "graph_event_id")
    private String graphEventId;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getRecurrence() { return recurrence; }
    public void setRecurrence(String recurrence) { this.recurrence = recurrence; }
    public String getOrganizerEmail() { return organizerEmail; }
    public void setOrganizerEmail(String organizerEmail) { this.organizerEmail = organizerEmail; }
    
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
    
    public String getGraphEventId() { return graphEventId; }
    public void setGraphEventId(String graphEventId) { this.graphEventId = graphEventId; }
}
