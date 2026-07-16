package Email_backend.Email_backend.service;

import Email_backend.Email_backend.dto.EventRequest;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class IcsService {

    public String generate(EventRequest req, String organizerEmail, Integer orgcode, Integer calid, Integer eventid) {

        DateTimeFormatter f =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

        String start = req.getStartTime()
            .atZone(ZoneId.of("Asia/Kolkata"))
            .withZoneSameInstant(ZoneOffset.UTC)
            .format(f);

        String end = req.getEndTime()
            .atZone(ZoneId.of("Asia/Kolkata"))
            .withZoneSameInstant(ZoneOffset.UTC)
            .format(f);

        String now =
            ZonedDateTime.now(ZoneOffset.UTC).format(f);

        String rrule = "";

        if(req.getRecurrence() != null) {
            switch(req.getRecurrence()) {
                case "DAILY": rrule = "RRULE:FREQ=DAILY\r\n"; break;
                case "WEEKLY": rrule = "RRULE:FREQ=WEEKLY\r\n"; break;
                case "MONTHLY": rrule = "RRULE:FREQ=MONTHLY\r\n"; break;
            }
        }

        boolean isGoogle = false;
        if (organizerEmail != null && organizerEmail.contains("@")) {
            String domain = organizerEmail.substring(organizerEmail.indexOf("@") + 1);
            if (MailConfigDetector.isGoogleDomain(domain)) {
                isGoogle = true;
            }
        }

        String prodId = isGoogle ? "-//Google Inc//Google Calendar 70.9054//EN" : "-//Calendar App//EN";
        
        StringBuilder attendeesBuilder = new StringBuilder();
        if (req.getAttendees() != null) {
            for (String attendee : req.getAttendees()) {
                if (attendee != null && !attendee.trim().isEmpty()) {
                    String name = attendee.contains("@") ? attendee.substring(0, attendee.indexOf("@")) : attendee;
                    attendeesBuilder.append("ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=")
                                    .append(name)
                                    .append(";X-NUM-GUESTS=0:mailto:")
                                    .append(attendee)
                                    .append("\r\n");
                }
            }
        }

        String organizerLine = "";
        if (organizerEmail != null && !organizerEmail.trim().isEmpty()) {
            String name = organizerEmail.contains("@") ? organizerEmail.substring(0, organizerEmail.indexOf("@")) : organizerEmail;
            organizerLine = "ORGANIZER;CN=" + name + ":mailto:" + organizerEmail + "\r\n";
        }
        
        String uidSuffix = isGoogle ? "@google.com" : "@botsuat.com";
        String uid = "event-" + orgcode + "-" + calid + "-" + eventid + uidSuffix;
        if (orgcode == null || calid == null || eventid == null) {
            uid = UUID.randomUUID().toString().replace("-", "") + uidSuffix;
        }

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("PRODID:").append(prodId).append("\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:REQUEST\r\n");
        ics.append("BEGIN:VEVENT\r\n");
        ics.append("DTSTART:").append(start).append("\r\n");
        ics.append("DTEND:").append(end).append("\r\n");
        ics.append("DTSTAMP:").append(now).append("\r\n");
        if (!organizerLine.isEmpty()) ics.append(organizerLine);
        ics.append("UID:").append(uid).append("\r\n");
        ics.append(attendeesBuilder.toString());
        
        if (isGoogle && req.getMeeturl() != null && !req.getMeeturl().isEmpty()) {
            ics.append("X-GOOGLE-CONFERENCE:").append(req.getMeeturl()).append("\r\n");
        } else if (req.getMeeturl() != null && !req.getMeeturl().isEmpty()) {
            ics.append("X-MICROSOFT-SKYPESPACES-ONLINE-MEETING:").append(req.getMeeturl()).append("\r\n");
        }

        ics.append("CREATED:").append(now).append("\r\n");
        
        String desc = req.getDescription() != null ? req.getDescription().replace("\n", "\\n") : "";
        ics.append("DESCRIPTION:").append(desc).append("\r\n");
        
        ics.append("LAST-MODIFIED:").append(now).append("\r\n");
        
        if (req.getLocation() != null && !req.getLocation().isEmpty()) {
            ics.append("LOCATION:").append(req.getLocation()).append("\r\n");
        } else if (req.getMeeturl() != null && !req.getMeeturl().isEmpty()) {
            ics.append("LOCATION:").append(req.getMeeturl()).append("\r\n");
        }
        
        ics.append("SEQUENCE:0\r\n");
        ics.append("STATUS:CONFIRMED\r\n");
        ics.append("SUMMARY:").append(req.getTitle()).append("\r\n");
        ics.append("TRANSP:OPAQUE\r\n");
        ics.append(rrule);
        
        // Alarm
        ics.append("BEGIN:VALARM\r\n");
        ics.append("ACTION:DISPLAY\r\n");
        ics.append("DESCRIPTION:This is an event reminder\r\n");
        ics.append("TRIGGER:-P0DT0H30M0S\r\n");
        ics.append("END:VALARM\r\n");
        
        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR");

        return ics.toString();
    }
}
