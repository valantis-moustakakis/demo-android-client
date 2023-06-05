package technology.moro.thesis.dtos;

import com.google.gson.Gson;

public class AuthenticationResponseDTO {
    private String token;
    private String message;

    public AuthenticationResponseDTO(String token, String message) {
        this.token = token;
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AuthenticationResponseDTO fromJsonString(String jsonString) {
        Gson gson = new Gson();
        return gson.fromJson(jsonString, AuthenticationResponseDTO.class);
    }
}
