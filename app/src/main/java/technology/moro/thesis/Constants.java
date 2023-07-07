package technology.moro.thesis;

import okhttp3.MediaType;

public class Constants {

    public static final String PREF_NAME = "MyAppPreferences";
    public static final String JWT_TOKEN_KEY = "thesis:jwtToken";
    public static final String EMAIL_KEY = "thesis:email";

    public static final String EMAIL_ADDRESS_REGEX = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,10}$";
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,30}$";

    public static final String BASE_URL = "https://192.168.1.128:8443/demo";
    public static final String AUTHENTICATION_URL = "/authentication";
    public static final String REGISTRATION_URL = "/registration";
    public static final String REPORT_URL = "/report";
    public static final String GET_REPORTS_URL = "/get-reports";
    public static final String GET_STREET_INFO_URL = "/get-street-info";
    public static final String OK_RESPONSE = "OK";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    public static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    public static final long LOCATION_UPDATE_INTERVAL = 3000; // 3 seconds

    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String ACCURACY = "accuracy";

    public static final String MQTT_BROKER_URL = "ssl://f0cdbc9159594b919d68036f1fc85241.s2.eu.hivemq.cloud:8883";
    public static final String MQTT_BROKER_USERNAME = "AndroidClient2023";
    public static final String MQTT_BROKER_PASSWORD = "AndroidClient2023";
    public static final String MQTT_BROKER_TOPIC = "measurements";

    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_LOW = "LOW";
    public static final String SEVERITY_NO_SEVERITY = "NO_SEVERITY";
    public static final String INCIDENT_IDENTIFICATION = "INCIDENT";
    public static final String STREET_IDENTIFICATION = "STREET";

    public static final String START_FOREGROUND_ACTION = "START_FOREGROUND_ACTION";
    public static final String STOP_FOREGROUND_ACTION = "STOP_FOREGROUND_ACTION";
}
