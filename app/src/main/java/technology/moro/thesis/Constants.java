package technology.moro.thesis;

import okhttp3.MediaType;

public class Constants {

    public static final String PREF_NAME = "MyAppPreferences";
    public static final String JWT_TOKEN_KEY = "thesis:jwtToken";
    public static final String EMAIL_KEY = "thesis:email";

    public static final String EMAIL_ADDRESS_REGEX = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,10}$";

    public static final String BASE_URL = "http://192.168.1.128:8080/demo";
    public static final String AUTHENTICATION_URL = "/authentication";
    public static final String REGISTRATION_URL = "/registration";
    public static final String REPORT_URL = "/report";
    public static final String GET_REPORTS_URL = "/get-reports";
    public static final String GET_STREET_INFO_URL = "/get-street-info";

    public static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
}
