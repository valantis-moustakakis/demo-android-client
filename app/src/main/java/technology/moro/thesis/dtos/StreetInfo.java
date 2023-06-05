package technology.moro.thesis.dtos;

public class StreetInfo {
    private String severity;
    private float latitude;
    private float longitude;

    public StreetInfo(String severity, float latitude, float longitude) {
        this.severity = severity;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getSeverity() {
        return severity;
    }

    public float getLatitude() {
        return latitude;
    }

    public float getLongitude() {
        return longitude;
    }
}
