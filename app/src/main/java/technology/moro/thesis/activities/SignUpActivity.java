package technology.moro.thesis.activities;

import static technology.moro.thesis.Constants.BASE_URL;
import static technology.moro.thesis.Constants.JSON_MEDIA_TYPE;
import static technology.moro.thesis.Constants.OK_RESPONSE;
import static technology.moro.thesis.Constants.REGISTRATION_URL;
import static technology.moro.thesis.Utils.createSSLSocketFactory;
import static technology.moro.thesis.Utils.createTrustManager;
import static technology.moro.thesis.validators.CredentialsValidator.validateConfirmPassword;
import static technology.moro.thesis.validators.CredentialsValidator.validateEmail;
import static technology.moro.thesis.validators.CredentialsValidator.validatePassword;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;

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

public class SignUpActivity extends AppCompatActivity {

    private EditText emailEditText;
    private EditText passwordEditText;
    private EditText confirmPasswordEditText;
    private TextInputLayout emailTextInputLayout;
    private TextInputLayout passwordTextInputLayout;
    private TextInputLayout confirmPasswordTextInputLayout;

    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        emailEditText = findViewById(R.id.email_edit_text);
        emailTextInputLayout = findViewById(R.id.email_input_layout);

        passwordEditText = findViewById(R.id.password_edit_text);
        passwordTextInputLayout = findViewById(R.id.password_input_layout);

        confirmPasswordEditText = findViewById(R.id.confirm_password_edit_text);
        confirmPasswordTextInputLayout = findViewById(R.id.confirm_password_input_layout);

        Button signUpButton = findViewById(R.id.sign_up_button);

        httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(createSSLSocketFactory(), createTrustManager()[0])
                .hostnameVerifier((hostname, session) -> true) // Bypass hostname verification
                .build();

        signUpButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();

            hideKeyboard();

            if (isInputValid(email, password, confirmPassword)) {
                performSignUp(email, password, confirmPassword);
            }
        });
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = this.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(this);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private boolean isInputValid(String email, String password, String confirmPassword) {
        boolean isValid = true;
        if (email.isEmpty() || !validateEmail(email)) {
            emailTextInputLayout.setError(getString(R.string.invalid_email));
            isValid = false;
        } else {
            emailTextInputLayout.setError(null);
        }

        if (password.isEmpty() || !validatePassword(password)) {
            passwordTextInputLayout.setError(getString(R.string.password_rules_error));
            isValid = false;
        } else {
            passwordTextInputLayout.setError(null);
        }

        if (confirmPassword.isEmpty() || !validateConfirmPassword(password, confirmPassword)) {
            confirmPasswordTextInputLayout.setError(getString(R.string.passwords_do_not_match));
            isValid = false;
        } else {
            confirmPasswordTextInputLayout.setError(null);
        }

        return isValid;
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
                showToast(getString(R.string.sign_up_failed_not_available));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String authenticationResponse = Objects.requireNonNull(response.body()).string();
                    if (OK_RESPONSE.equals(authenticationResponse)) {
                        navigateToLogin();
                    } else {
                        showToast("Sign Up failed:\n" + authenticationResponse);
                    }
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
        runOnUiThread(() -> {
            Toast t = Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_LONG);
            t.show();
        });
    }
}
