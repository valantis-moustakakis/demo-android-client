package technology.moro.thesis.dtos;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

public class TransmissionDataDTO {
    private long ts;
    private String user;
    private float lon;
    private float lat;
    private float x;
    private float y;
    private float z;

    public TransmissionDataDTO(long timestamp, String user, float longitude, float latitude, float x, float y, float z) {
        this.ts = timestamp;
        this.user = user;
        this.lon = longitude;
        this.lat = latitude;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public float getLon() {
        return lon;
    }

    public void setLon(float lon) {
        this.lon = lon;
    }

    public float getLat() {
        return lat;
    }

    public void setLat(float lat) {
        this.lat = lat;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getZ() {
        return z;
    }

    public void setZ(float z) {
        this.z = z;
    }

    @NonNull
    @Override
    public String toString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
