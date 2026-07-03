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

    public String generate(EventRequest req, String organizerEmail) {

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

                case "DAILY":
                    rrule = "RRULE:FREQ=DAILY\r\n";
                    break;

                case "WEEKLY":
                    rrule = "RRULE:FREQ=WEEKLY\r\n";
                    break;

                case "MONTHLY":
                    rrule = "RRULE:FREQ=MONTHLY\r\n";
                    break;
            }
        }

        StringBuilder attendeesBuilder = new StringBuilder();
        if (req.getAttendees() != null) {
            for (String attendee : req.getAttendees()) {
                if (attendee != null && !attendee.trim().isEmpty()) {
                    attendeesBuilder.append("ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=")
                                    .append(attendee)
                                    .append(":mailto:")
                                    .append(attendee)
                                    .append("\r\n");
                }
            }
        }

        String organizerLine = "";
        if (organizerEmail != null && !organizerEmail.trim().isEmpty()) {
            organizerLine = "ORGANIZER;CN=" + organizerEmail + ":mailto:" + organizerEmail + "\r\n";
        }

        return "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//Calendar App//EN\r\n" +
                "METHOD:REQUEST\r\n" +
                "BEGIN:VEVENT\r\n" +
                "UID:" + UUID.randomUUID() + "\r\n" +
                "DTSTAMP:" + now + "\r\n" +
                "DTSTART:" + start + "\r\n" +
                "DTEND:" + end + "\r\n" +
                "SUMMARY:" + req.getTitle() + "\r\n" +
                "DESCRIPTION:" + req.getDescription() + "\r\n" +
                "LOCATION:" + req.getLocation() + "\r\n" +
                organizerLine +
                attendeesBuilder.toString() +
                rrule +
                "END:VEVENT\r\n" +
                "END:VCALENDAR";
    }
}
