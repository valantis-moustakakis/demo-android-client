package technology.moro.thesis.activities;

import static technology.moro.thesis.Constants.BASE_URL;
import static technology.moro.thesis.Constants.JSON_MEDIA_TYPE;
import static technology.moro.thesis.Constants.REGISTRATION_URL;
import static technology.moro.thesis.validators.CredentialsValidator.validateConfirmPassword;
import static technology.moro.thesis.validators.CredentialsValidator.validateEmail;
import static technology.moro.thesis.validators.CredentialsValidator.validatePassword;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import technology.moro.thesis.R;
import technology.moro.thesis.dtos.AuthenticationDTO;
import technology.moro.thesis.dtos.AuthenticationResponseDTO;

public class SignUpActivity extends AppCompatActivity {

    private EditText emailEditText;
    private EditText passwordEditText;
    private EditText confirmPasswordEditText;

    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        confirmPasswordEditText = findViewById(R.id.confirm_password_edit_text);
        Button signUpButton = findViewById(R.id.sign_up_button);

        httpClient = new OkHttpClient();

        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();
                String confirmPassword = confirmPasswordEditText.getText().toString().trim();

                if (isInputValid(email, password, confirmPassword)) {
                    performSignUp(email, password, confirmPassword);
                }
            }
        });
    }

    private boolean isInputValid(String email, String password, String confirmPassword) {
        if (email.isEmpty() || !validateEmail(email)) {
            showToast("Invalid email");
            return false;
        }

        if (password.isEmpty() || !validatePassword(password)) {
            showToast("Password must be between 7 and 30 characters long");
            return false;
        }

        if (confirmPassword.isEmpty() || !validateConfirmPassword(password, confirmPassword)) {
            showToast("Passwords do not match");
            return false;
        }

        return true;
    }

    private void performSignUp(String email, String password, String confirmPassword) {
        AuthenticationDTO authentication = new AuthenticationDTO(email, password, confirmPassword);
        RequestBody body = RequestBody.create(authentication.toJsonString(), JSON_MEDIA_TYPE);

        Request request = new Request.Builder()
                .url(BASE_URL + REGISTRATION_URL)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                showToast("Sign Up failed");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    navigateToLogin();
                } else {
                    String stringAuthenticationResponse = Objects.requireNonNull(response.body()).string();
                    Gson gson = new Gson();
                    AuthenticationResponseDTO authenticationResponse = gson.fromJson(stringAuthenticationResponse, AuthenticationResponseDTO.class);
                    showToast("Sign Up failed:\n" + authenticationResponse.getMessage());
                }
                response.close();
            }
        });
    }

    private void navigateToLogin() {
        Intent loginIntent = new Intent(SignUpActivity.this, LoginActivity.class);
        loginIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(loginIntent);
        finish();
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast t = Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_SHORT);
                t.setGravity(Gravity.FILL_HORIZONTAL, 0, 0);
                t.show();
            }
        });
    }
}
