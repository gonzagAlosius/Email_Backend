package Email_backend.Email_backend.service;

import Email_backend.Email_backend.model.UserEmailConfig;
import Email_backend.Email_backend.repository.UserEmailConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.mail.*;
import java.util.List;
import java.util.Properties;

/**
 * InboxPollingService
 *
 * Runs a background scheduled task every 60 seconds that:
 *  1. Loads all active users who have a registered OneSignal subscription ID.
 *  2. Connects to each user's IMAP INBOX and checks the total message count.
 *  3. Compares against the last known count stored in the database.
 *  4. If new messages are found, reads the latest message's sender & subject
 *     and fires a push notification via OneSignalService.
 *  5. Updates the stored count so the same emails are not notified again.
 *
 * This is a lightweight "poll & push" approach that works without requiring
 * persistent IMAP IDLE connections or webhooks from the mail server.
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

    /**
     * Runs every 60 seconds (60000 ms).
     * Initial delay of 30 seconds to let the app fully start up.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void pollAllUsersForNewEmails() {
        List<UserEmailConfig> pushUsers =
                userEmailConfigRepository.findByIsActiveTrueAndOneSignalSubscriptionIdIsNotNull();

        if (pushUsers.isEmpty()) {
            return; // No users registered for push — nothing to do
        }

        System.out.println("[InboxPoller] Checking " + pushUsers.size() + " user(s) for new emails...");

        for (UserEmailConfig user : pushUsers) {
            try {
                checkUserInbox(user);
            } catch (Exception e) {
                System.err.println("[InboxPoller] Error checking inbox for "
                        + user.getEmailAddress() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Connects to a single user's IMAP INBOX, compares message count,
     * and sends a push if new messages are detected.
     */
    private void checkUserInbox(UserEmailConfig user) throws Exception {
        String email = user.getEmailAddress();
        String password = encryptionService.decrypt(user.getEncryptedPassword());

        // Resolve OAuth token if needed (e.g. Microsoft domains)
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
        // Set short timeouts so a stuck server doesn't block the entire poll cycle
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

            if (lastKnown == 0) {
                // First poll — just store the count, don't flood with old notifications
                user.setLastKnownInboxCount(currentCount);
                userEmailConfigRepository.save(user);
                System.out.println("[InboxPoller] Initial count for " + email + ": " + currentCount);
            } else if (currentCount > lastKnown) {
                int newCount = currentCount - lastKnown;
                System.out.println("[InboxPoller] " + newCount + " new email(s) for " + email);

                // Read the latest message to build a meaningful notification
                Message latestMsg = inbox.getMessage(currentCount);
                // Pre-fetch envelope to avoid extra round-trips
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

                // Build notification text
                String title = "📧 " + sender;
                String body;
                if (newCount == 1) {
                    body = subject;
                } else {
                    body = subject + " (+" + (newCount - 1) + " more)";
                }

                // Send push via OneSignal using external_id (= user's email)
                oneSignalService.sendPushToExternalId(email, title, body, sender);

                // Update the stored count
                user.setLastKnownInboxCount(currentCount);
                userEmailConfigRepository.save(user);
            }
            // If currentCount <= lastKnown, no new emails — nothing to do

            inbox.close(false);
        } finally {
            if (store.isConnected()) {
                store.close();
            }
        }
    }
}
