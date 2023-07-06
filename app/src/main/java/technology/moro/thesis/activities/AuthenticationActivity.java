package technology.moro.thesis.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import technology.moro.thesis.R;

public class AuthenticationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);

        Button loginButton = findViewById(R.id.login_button);
        Button signUpButton = findViewById(R.id.signup_button);

        loginButton.setOnClickListener(v -> navigateToLogin());
        signUpButton.setOnClickListener(v -> navigateToSignUp());
    }

    private void navigateToLogin() {
        Intent loginIntent = new Intent(AuthenticationActivity.this, LoginActivity.class);
        startActivity(loginIntent);
    }

    private void navigateToSignUp() {
        Intent signUpIntent = new Intent(AuthenticationActivity.this, SignUpActivity.class);
        startActivity(signUpIntent);
    }
}
