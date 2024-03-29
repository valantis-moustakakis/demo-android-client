package technology.moro.thesis.activities;

import static technology.moro.thesis.Constants.EMAIL_KEY;
import static technology.moro.thesis.Constants.JWT_TOKEN_KEY;
import static technology.moro.thesis.Constants.PREF_NAME;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import technology.moro.thesis.R;

public class HomeActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Button measurementButton = findViewById(R.id.measurement_button);
        Button reportButton = findViewById(R.id.report_button);
        Button mapButton = findViewById(R.id.map_button);
        Button logoutButton = findViewById(R.id.logout_button);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        measurementButton.setOnClickListener(v -> navigateToMeasurement());
        reportButton.setOnClickListener(v -> navigateToReportIncident());
        mapButton.setOnClickListener(v -> navigateToMap());
        logoutButton.setOnClickListener(v -> logout());
    }

    private void navigateToMeasurement() {
        Intent measurementIntent = new Intent(HomeActivity.this, MeasurementActivity.class);
        startActivity(measurementIntent);
    }

    private void navigateToReportIncident() {
        Intent reportIntent = new Intent(HomeActivity.this, ReportIncidentActivity.class);
        startActivity(reportIntent);
    }

    private void navigateToMap() {
        Intent mapIntent = new Intent(HomeActivity.this, MapActivity.class);
        startActivity(mapIntent);
    }

    private void logout() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(JWT_TOKEN_KEY);
        editor.remove(EMAIL_KEY);
        editor.apply();

        Intent authIntent = new Intent(HomeActivity.this, AuthenticationActivity.class);
        authIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(authIntent);
        finish();
    }
}
