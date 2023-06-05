package technology.moro.thesis.activities;

import static technology.moro.thesis.Constants.BASE_URL;
import static technology.moro.thesis.Constants.JSON_MEDIA_TYPE;
import static technology.moro.thesis.Constants.REGISTRATION_URL;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import technology.moro.thesis.R;
import technology.moro.thesis.dtos.AuthenticationDTO;

public class SignUpActivity extends AppCompatActivity {

    private EditText emailEditText;
    private EditText passwordEditText;
    private Button signUpButton;

    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        signUpButton = findViewById(R.id.sign_up_button);

        httpClient = new OkHttpClient();

        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();

                if (isInputValid(email, password)) {
                    performSignUp(email, password);
                }
            }
        });
    }

    private boolean isInputValid(String email, String password) {
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast("Invalid email");
            return false;
        }

        if (password.isEmpty() || password.length() < 6) {
            showToast("Invalid password");
            return false;
        }

        return true;
    }

    private void performSignUp(String email, String password) {
        AuthenticationDTO authentication = new AuthenticationDTO(email, password);
        RequestBody body = RequestBody.create(authentication.toJsonString(), JSON_MEDIA_TYPE);

        Request request = new Request.Builder()
                .url(BASE_URL + REGISTRATION_URL)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                showToast("Sign Up failed");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    navigateToLogin();
                } else {
                    showToast("Sign Up failed");
                }
                response.close();
            }
        });
    }

    private void navigateToLogin() {
        Intent loginIntent = new Intent(SignUpActivity.this, LoginActivity.class);
        startActivity(loginIntent);
        finish();
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
