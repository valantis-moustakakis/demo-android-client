package technology.moro.thesis.dtos;

public class Street {
    private String severity;
    private float latitude;
    private float longitude;
    private String lastRetrievalDate;

    public Street(String severity, float latitude, float longitude, String lastRetrievalDate) {
        this.severity = severity;
        this.latitude = latitude;
        this.longitude = longitude;
        this.lastRetrievalDate = lastRetrievalDate;
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

    public String getLastRetrievalDate() {
        return lastRetrievalDate;
    }
}
