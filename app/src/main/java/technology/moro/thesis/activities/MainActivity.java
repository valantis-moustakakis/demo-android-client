package technology.moro.thesis.activities;

import static technology.moro.thesis.Constants.JWT_TOKEN_KEY;
import static technology.moro.thesis.Constants.PREF_NAME;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;

import technology.moro.thesis.R;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Show loading icon
        progressBar = findViewById(R.id.progress_bar);
        progressBar.setVisibility(View.VISIBLE);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Delayed execution to simulate loading or network request
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAndNavigate();
            }
        }, 1000); // Adjust the delay time as needed
    }

    private void checkAndNavigate() {
        // Check if JWT token exists in SharedPreferences
        String jwtToken = sharedPreferences.getString(JWT_TOKEN_KEY, null);

        if (jwtToken != null) {
            // JWT token exists, navigate to HomeActivity
            Intent homeIntent = new Intent(MainActivity.this, HomeActivity.class);
            startActivity(homeIntent);
        } else {
            // No JWT token, navigate to AuthenticationActivity
            Intent authIntent = new Intent(MainActivity.this, AuthenticationActivity.class);
            startActivity(authIntent);
        }

        // Finish the MainActivity so that the user can't navigate back to it
        finish();
    }
}