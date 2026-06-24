package Email_backend.Email_backend.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mail102")
public class UserEmailConfig {

    @Id
    @Column(name = "mailbox_id", columnDefinition = "UUID")
    private UUID mailboxId;

    @Column(name = "orgcode", nullable = false)
    private Long orgcode;

    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    private UUID userId;

    @Column(name = "email_address", nullable = false, length = 255)
    private String emailAddress;

    @Column(name = "encrypted_password", nullable = false, columnDefinition = "TEXT")
    private String encryptedPassword;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "euser", columnDefinition = "TEXT")
    private String euser;

    @Column(name = "edate")
    private LocalDateTime edate;

    @Column(name = "cuser", columnDefinition = "TEXT")
    private String cuser;

    @Column(name = "cdate")
    private LocalDateTime cdate;

    @Column(name = "auser", columnDefinition = "TEXT")
    private String auser;

    @Column(name = "adate")
    private LocalDateTime adate;

    /**
     * Stores the OneSignal push notification subscription ID for this device/user.
     * Populated when the Flutter app calls POST /api/notifications/register after login.
     */
    @Column(name = "onesignal_subscription_id", columnDefinition = "TEXT")
    private String oneSignalSubscriptionId;

    /**
     * Stores the last known INBOX message count for this user.
     * Used by InboxPollingService to detect new emails.
     */
    @Column(name = "last_known_inbox_count")
    private Integer lastKnownInboxCount;

    public UserEmailConfig() {
    }

    // Getters and Setters
    public UUID getMailboxId() {
        return mailboxId;
    }

    public void setMailboxId(UUID mailboxId) {
        this.mailboxId = mailboxId;
    }

    public Long getOrgcode() {
        return orgcode;
    }

    public void setOrgcode(Long orgcode) {
        this.orgcode = orgcode;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getEuser() {
        return euser;
    }

    public void setEuser(String euser) {
        this.euser = euser;
    }

    public LocalDateTime getEdate() {
        return edate;
    }

    public void setEdate(LocalDateTime edate) {
        this.edate = edate;
    }

    public String getCuser() {
        return cuser;
    }

    public void setCuser(String cuser) {
        this.cuser = cuser;
    }

    public LocalDateTime getCdate() {
        return cdate;
    }

    public void setCdate(LocalDateTime cdate) {
        this.cdate = cdate;
    }

    public String getAuser() {
        return auser;
    }

    public void setAuser(String auser) {
        this.auser = auser;
    }

    public LocalDateTime getAdate() {
        return adate;
    }

    public void setAdate(LocalDateTime adate) {
        this.adate = adate;
    }

    public String getOneSignalSubscriptionId() {
        return oneSignalSubscriptionId;
    }

    public void setOneSignalSubscriptionId(String oneSignalSubscriptionId) {
        this.oneSignalSubscriptionId = oneSignalSubscriptionId;
    }

    public Integer getLastKnownInboxCount() {
        return lastKnownInboxCount;
    }

    public void setLastKnownInboxCount(Integer lastKnownInboxCount) {
        this.lastKnownInboxCount = lastKnownInboxCount;
    }
}
