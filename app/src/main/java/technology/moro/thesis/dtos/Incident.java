package technology.moro.thesis.dtos;

import java.util.Date;

public class Incident {
    private Long reportId;
    private String userEmail;
    private String severity;
    private String description;
    private float latitude;
    private float longitude;
    private Date reportDate;

    public Incident(Long reportId, String userEmail, String severity, String description,
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

    public String getUserEmail() {
        return userEmail;
    }

    public String getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public float getLatitude() {
        return latitude;
    }

    public float getLongitude() {
        return longitude;
    }

    public Date getReportDate() {
        return reportDate;
    }
}
