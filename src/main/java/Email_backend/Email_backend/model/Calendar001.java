package Email_backend.Email_backend.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "calendar001")
public class Calendar001 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer calid;
    private Integer orgcode;
    private String userid;
    private String calname;
    private String timezone;
    private String country;
    private String euser;
    private LocalDateTime edate;
    private String cuser;
    private LocalDateTime cdate;
    private String auser;
    private LocalDateTime adate;

    // Getters and Setters
    public Integer getCalid() {
        return calid;
    }

    public void setCalid(Integer calid) {
        this.calid = calid;
    }

    public Integer getOrgcode() {
        return orgcode;
    }

    public void setOrgcode(Integer orgcode) {
        this.orgcode = orgcode;
    }

    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public String getCalname() {
        return calname;
    }

    public void setCalname(String calname) {
        this.calname = calname;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
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
