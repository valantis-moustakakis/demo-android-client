package technology.moro.thesis.activities;

import static technology.moro.thesis.Constants.AUTHENTICATION_URL;
import static technology.moro.thesis.Constants.BASE_URL;
import static technology.moro.thesis.Constants.EMAIL_KEY;
import static technology.moro.thesis.Constants.JSON_MEDIA_TYPE;
import static technology.moro.thesis.Constants.JWT_TOKEN_KEY;
import static technology.moro.thesis.Constants.PREF_NAME;
import static technology.moro.thesis.validators.CredentialsValidator.validateEmail;
import static technology.moro.thesis.validators.CredentialsValidator.validatePassword;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import technology.moro.thesis.R;
import technology.moro.thesis.dtos.AuthenticationDTO;
import technology.moro.thesis.dtos.AuthenticationResponseDTO;

public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;

    private SharedPreferences sharedPreferences;
    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        loginButton = findViewById(R.id.login_button);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        httpClient = new OkHttpClient();

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();

                if (isInputValid(email, password)) {
                    performLogin(email, password);
                }
            }
        });
    }

    private boolean isInputValid(String email, String password) {
        if (email.isEmpty() || !validateEmail(email)) {
            showToast("Invalid email");
            return false;
        }

        if (password.isEmpty() || !validatePassword(password)) {
            showToast("Invalid password");
            return false;
        }

        return true;
    }

    private void performLogin(String email, String password) {
        AuthenticationDTO authentication = new AuthenticationDTO(email, password);
        RequestBody body = RequestBody.create(authentication.toJsonString(), JSON_MEDIA_TYPE);

        Request request = new Request.Builder()
                .url(BASE_URL + AUTHENTICATION_URL)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                showToast("Login failed");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String stringAuthenticationResponse = response.body().string();
                Gson gson = new Gson();
                AuthenticationResponseDTO authenticationResponse = gson.fromJson(stringAuthenticationResponse, AuthenticationResponseDTO.class);
                if (response.isSuccessful()) {
                    saveToSharedPreferences(email, authenticationResponse.getToken());
                    navigateToHome();
                } else {
                    showToast("Login failed:\n" + authenticationResponse.getMessage());
                }
                response.close();
            }
        });
    }

    private void saveToSharedPreferences(String email, String jwtToken) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(EMAIL_KEY, email);
        editor.putString(JWT_TOKEN_KEY, jwtToken);
        editor.apply();
    }

    private void navigateToHome() {
        Intent homeIntent = new Intent(LoginActivity.this, HomeActivity.class);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
        finish();
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast t = Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT);
                t.setGravity(Gravity.FILL_HORIZONTAL, 0, 0);
                t.show();
            }
        });
    }
}
