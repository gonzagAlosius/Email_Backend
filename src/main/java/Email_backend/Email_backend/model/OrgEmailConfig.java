package Email_backend.Email_backend.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mail101")
public class OrgEmailConfig {

    @Id
    @Column(name = "orgcode", nullable = false)
    private Long orgcode;

    @Column(name = "imap_host", length = 255)
    private String imapHost;

    @Column(name = "imap_port")
    private Integer imapPort;

    @Column(name = "imap_secure")
    private Boolean imapSecure;

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_secure")
    private Boolean smtpSecure;

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

    public OrgEmailConfig() {
    }

    // Getters and Setters
    public Long getOrgcode() {
        return orgcode;
    }

    public void setOrgcode(Long orgcode) {
        this.orgcode = orgcode;
    }

    public String getImapHost() {
        return imapHost;
    }

    public void setImapHost(String imapHost) {
        this.imapHost = imapHost;
    }

    public Integer getImapPort() {
        return imapPort;
    }

    public void setImapPort(Integer imapPort) {
        this.imapPort = imapPort;
    }

    public Boolean getImapSecure() {
        return imapSecure;
    }

    public void setImapSecure(Boolean imapSecure) {
        this.imapSecure = imapSecure;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public Integer getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(Integer smtpPort) {
        this.smtpPort = smtpPort;
    }

    public Boolean getSmtpSecure() {
        return smtpSecure;
    }

    public void setSmtpSecure(Boolean smtpSecure) {
        this.smtpSecure = smtpSecure;
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
}
