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

    public String generate(EventRequest req) {

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
                rrule +
                "END:VEVENT\r\n" +
                "END:VCALENDAR";
    }
}
