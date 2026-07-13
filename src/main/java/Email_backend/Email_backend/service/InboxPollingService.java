package Email_backend.Email_backend.service;

import Email_backend.Email_backend.model.UserEmailConfig;
import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;

/**
 * InboxPollingService
 *
 * Runs a background scheduled task every 60 seconds that:
 *  1. Loads all active users who have a registered OneSignal subscription ID.
 *  2. Connects to each user's IMAP INBOX and checks the total message count.
 *  3. Compares against the last known count stored in the database.
 *  4. If new messages are found, reads them and checks for ICS calendar replies.
 *  5. Fires a push notification via OneSignalService for new emails.
 *  6. Updates the stored count so the same emails are not notified again.
 */
@Service
public class InboxPollingService {

    @Autowired
    private UserEmailConfigRepository userEmailConfigRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private OrgEmailConfigService orgEmailConfigService;

    @Autowired
    private OneSignalService oneSignalService;

    @Autowired
    private UnifiedCalendarService unifiedCalendarService;

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void pollAllUsersForNewEmails() {
        // We now fetch ALL active users to ensure calendar replies are parsed, 
        // regardless of whether they have a push notification ID registered.
        List<UserEmailConfig> activeUsers =
                userEmailConfigRepository.findByIsActiveTrue();

        if (activeUsers.isEmpty()) {
            return;
        }

        System.out.println("[InboxPoller] Checking " + activeUsers.size() + " user(s) for calendar replies and new emails...");

        for (UserEmailConfig user : activeUsers) {
            try {
                checkUserInbox(user);
            } catch (Exception e) {
                System.err.println("[InboxPoller] Error checking inbox for "
                        + user.getEmailAddress() + ": " + e.getMessage());
            }
        }
    }

    private void checkUserInbox(UserEmailConfig user) throws Exception {
        String email = user.getEmailAddress();
        String password = encryptionService.decrypt(user.getEncryptedPassword());

        String resolvedPassword = MailConfigDetector.resolvePassword(email, password);
        MailConfigDetector.Config config = orgEmailConfigService.getMailConfig(email, resolvedPassword);

        boolean isSecure = config.getImapSecure();
        String protocol = isSecure ? "imaps" : "imap";

        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", config.getImapHost());
        props.put("mail." + protocol + ".port", config.getImapPort());
        if (isSecure) {
            props.put("mail.imaps.ssl.trust", config.getImapHost());
        }
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "10000");

        if (MailConfigDetector.isOAuthToken(resolvedPassword)) {
            props.put("mail." + protocol + ".auth.mechanisms", "XOAUTH2");
            props.put("mail." + protocol + ".auth.login.disable", "true");
            props.put("mail." + protocol + ".auth.plain.disable", "true");
        }

        Session session = Session.getInstance(props, null);
        Store store = session.getStore(protocol);

        try {
            store.connect(config.getImapHost(), email, resolvedPassword);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            int currentCount = inbox.getMessageCount();
            int lastKnown = user.getLastKnownInboxCount() != null
                    ? user.getLastKnownInboxCount() : 0;
                    
            // 1. Unconditionally check the last 5 messages in the inbox for Calendar Replies
            // This guarantees we parse RSVPs even if the message was read or if the total count skewed.
            int startCount = Math.max(1, currentCount - 4);
            for (int i = startCount; i <= currentCount; i++) {
                try {
                    Message msg = inbox.getMessage(i);
                    processMessageForCalendarReplies(msg);
                } catch (Exception ex) {
                    System.err.println("[InboxPoller] Error processing message " + i + " for calendar parsing: " + ex.getMessage());
                }
            }

            // 2. Handle Push Notifications for genuinely new messages
            if (lastKnown == 0) {
                user.setLastKnownInboxCount(currentCount);
                userEmailConfigRepository.save(user);
                System.out.println("[InboxPoller] Initial count for " + email + ": " + currentCount);
            } else if (currentCount > lastKnown) {
                int newCount = currentCount - lastKnown;
                System.out.println("[InboxPoller] " + newCount + " new email(s) for " + email);

                Message latestMsg = null;
                for (int i = lastKnown + 1; i <= currentCount; i++) {
                    try {
                        latestMsg = inbox.getMessage(i);
                    } catch (Exception ex) {
                        System.err.println("[InboxPoller] Error fetching message " + i + ": " + ex.getMessage());
                    }
                }

                if (latestMsg != null && user.getOneSignalSubscriptionId() != null) {
                    FetchProfile fp = new FetchProfile();
                    fp.add(FetchProfile.Item.ENVELOPE);
                    inbox.fetch(new Message[]{latestMsg}, fp);

                    String sender = "Someone";
                    Address[] froms = latestMsg.getFrom();
                    if (froms != null && froms.length > 0) {
                        String fromStr = froms[0].toString();
                        if (fromStr.contains("<")) {
                            sender = fromStr.substring(0, fromStr.indexOf("<")).trim().replace("\"", "");
                            if (sender.isEmpty()) {
                                sender = fromStr.substring(fromStr.indexOf("<") + 1, fromStr.indexOf(">"));
                            }
                        } else {
                            sender = fromStr;
                        }
                    }

                    String subject = latestMsg.getSubject() != null
                            ? latestMsg.getSubject() : "(No Subject)";

                    String title = "📧 " + sender;
                    String body;
                    if (newCount == 1) {
                        body = subject;
                    } else {
                        body = subject + " (+" + (newCount - 1) + " more)";
                    }

                    oneSignalService.sendPushToExternalId(email, title, body, sender);
                }

                user.setLastKnownInboxCount(currentCount);
                userEmailConfigRepository.save(user);
            }

            inbox.close(false);
        } finally {
            if (store.isConnected()) {
                store.close();
            }
        }
    }

    private void processMessageForCalendarReplies(Message msg) {
        try {
            logDebug("Processing message: " + msg.getSubject() + " | Content-Type: " + msg.getContentType());
            if (msg.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) msg.getContent();
                logDebug("Message is multipart with " + mp.getCount() + " parts.");
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart part = mp.getBodyPart(i);
                    logDebug("Part " + i + " Content-Type: " + part.getContentType() + " | FileName: " + part.getFileName());
                    if (part.isMimeType("text/calendar") || (part.getFileName() != null && part.getFileName().toLowerCase().endsWith(".ics"))) {
                        InputStream is = part.getInputStream();
                        Scanner s = new Scanner(is).useDelimiter("\\A");
                        String icsContent = s.hasNext() ? s.next() : "";
                        is.close();
                        
                        if (!icsContent.isEmpty()) {
                            parseIcsAndUpdateStatus(icsContent);
                        }
                    } else if (part.isMimeType("multipart/*")) {
                        // Sometimes the ICS is nested inside another multipart
                        processNestedMultipart((Multipart) part.getContent());
                    }
                }
            } else if (msg.isMimeType("text/calendar") || (msg.getFileName() != null && msg.getFileName().toLowerCase().endsWith(".ics"))) {
                logDebug("Message is directly text/calendar or .ics");
                InputStream is = msg.getInputStream();
                Scanner s = new Scanner(is).useDelimiter("\\A");
                String icsContent = s.hasNext() ? s.next() : "";
                is.close();
                if (!icsContent.isEmpty()) {
                    parseIcsAndUpdateStatus(icsContent);
                }
            } else {
                logDebug("Message is not multipart and not text/calendar. Skipping ICS check.");
            }
        } catch (Exception e) {
            System.err.println("[InboxPoller] Failed to parse calendar attachment: " + e.getMessage());
            logDebug("Exception in processMessageForCalendarReplies: " + e.getMessage());
        }
    }
    
    private void processNestedMultipart(Multipart mp) {
        try {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                if (part.isMimeType("text/calendar") || (part.getFileName() != null && part.getFileName().toLowerCase().endsWith(".ics"))) {
                    InputStream is = part.getInputStream();
                    Scanner s = new Scanner(is).useDelimiter("\\A");
                    String icsContent = s.hasNext() ? s.next() : "";
                    is.close();
                    
                    if (!icsContent.isEmpty()) {
                        parseIcsAndUpdateStatus(icsContent);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[InboxPoller] Failed to parse nested calendar attachment: " + e.getMessage());
        }
    }

    private void logDebug(String msg) {
        try {
            java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(System.getProperty("user.home") + "/Desktop/inbox_poll_debug.txt", true));
            pw.println(new java.util.Date() + " - " + msg);
            pw.close();
        } catch (Exception e) {}
    }

    private void parseIcsAndUpdateStatus(String icsContent) {
        logDebug("Found ICS content. Length: " + icsContent.length());
        
        // Unfold lines to make regex matching easier (ICS lines are wrapped with CRLF + SPACE)
        String unfoldedIcs = icsContent.replaceAll("(\\r?\\n)[ \\t]", "");
        
        if (!unfoldedIcs.toUpperCase().contains("METHOD:REPLY")) {
            logDebug("ICS does not contain METHOD:REPLY. Skipping.");
            return;
        }

        logDebug("ICS contains METHOD:REPLY. Parsing UID...");

        Matcher uidMatcher = Pattern.compile("UID:event-(\\d+)-(\\d+)-(\\d+)@", Pattern.CASE_INSENSITIVE).matcher(unfoldedIcs);
        if (uidMatcher.find()) {
            try {
                Integer orgcode = Integer.parseInt(uidMatcher.group(1));
                Integer calid = Integer.parseInt(uidMatcher.group(2));
                Integer eventid = Integer.parseInt(uidMatcher.group(3));
                logDebug("Found UID match! orgcode=" + orgcode + ", calid=" + calid + ", eventid=" + eventid);

                Matcher attendeeMatcher = Pattern.compile("ATTENDEE(.*?)[\\r\\n]", Pattern.CASE_INSENSITIVE).matcher(unfoldedIcs);
                boolean foundAttendee = false;
                while (attendeeMatcher.find()) {
                    String line = attendeeMatcher.group(1);
                    Matcher psMatcher = Pattern.compile("PARTSTAT=([^;:]+)", Pattern.CASE_INSENSITIVE).matcher(line);
                    Matcher emailMatcher = Pattern.compile("mailto:([^;:\\r\\n]+)", Pattern.CASE_INSENSITIVE).matcher(line);
                    
                    if (psMatcher.find() && emailMatcher.find()) {
                        foundAttendee = true;
                        String partstat = psMatcher.group(1).trim().toUpperCase(); // e.g. ACCEPTED, DECLINED
                        String attendeeEmail = emailMatcher.group(1).trim();
                        
                        logDebug("Found ATTENDEE match! email=" + attendeeEmail + ", partstat=" + partstat);
                        unifiedCalendarService.updateAttendeeResponseStatus(orgcode, calid, eventid, attendeeEmail, partstat);
                        System.out.println("[InboxPoller] Updated RSVP for " + attendeeEmail + " to " + partstat);
                        logDebug("Successfully updated database for " + attendeeEmail);
                    }
                }
                if (!foundAttendee) {
                    logDebug("No ATTENDEE with PARTSTAT and mailto found in ICS string!");
                    logDebug("Raw Unfolded ICS:\n" + unfoldedIcs);
                }
            } catch (Exception e) {
                System.err.println("[InboxPoller] Error parsing ICS details: " + e.getMessage());
                logDebug("Error parsing ICS details: " + e.getMessage());
            }
        } else {
            logDebug("UID did not match event-orgcode-calid-eventid@ pattern.");
            logDebug("Raw Unfolded ICS:\n" + unfoldedIcs);
        }
    }
}
