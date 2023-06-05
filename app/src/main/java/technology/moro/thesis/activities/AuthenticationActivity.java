package technology.moro.thesis.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import technology.moro.thesis.R;

public class AuthenticationActivity extends AppCompatActivity {

    private Button loginButton;
    private Button signUpButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);

        loginButton = findViewById(R.id.login_button);
        signUpButton = findViewById(R.id.signup_button);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToLogin();
            }
        });

        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToSignUp();
            }
        });
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
