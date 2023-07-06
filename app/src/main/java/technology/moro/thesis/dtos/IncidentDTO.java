package technology.moro.thesis.dtos;

import java.util.Date;

public class IncidentDTO {
    private Long reportId;
    private String userEmail;
    private String severity;
    private String description;
    private float latitude;
    private float longitude;
    private Date reportDate;

    public IncidentDTO(Long reportId, String userEmail, String severity, String description,
                       float latitude, float longitude, Date reportDate) {
        this.reportId = reportId;
        this.userEmail = userEmail;
        this.severity = severity;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.reportDate = reportDate;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public float getLatitude() {
        return latitude;
    }

    public void setLatitude(float latitude) {
        this.latitude = latitude;
    }

    public float getLongitude() {
        return longitude;
    }

    public void setLongitude(float longitude) {
        this.longitude = longitude;
    }

    public Date getReportDate() {
        return reportDate;
    }

    public void setReportDate(Date reportDate) {
        this.reportDate = reportDate;
    }
}
