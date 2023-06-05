package technology.moro.thesis.dtos;

import com.google.gson.Gson;

public class ReportDTO {

    private Long reportId;
    private String email;
    private String severity;
    private String description;
    private float latitude;
    private float longitude;

    public ReportDTO(String email, String severity, String description, float latitude, float longitude) {
        this.email = email;
        this.severity = severity;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() {
        return reportId;
    }

    public void setId(Long reportId) {
        this.reportId = reportId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String toJsonString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
