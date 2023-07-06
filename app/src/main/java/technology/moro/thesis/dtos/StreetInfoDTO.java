package technology.moro.thesis.dtos;

public class StreetInfoDTO {
    private String severity;
    private float latitude;
    private float longitude;

    public StreetInfoDTO(String severity, float latitude, float longitude) {
        this.severity = severity;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
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
}
