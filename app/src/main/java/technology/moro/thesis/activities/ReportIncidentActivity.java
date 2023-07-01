package technology.moro.thesis.activities;

import static technology.moro.thesis.Constants.BASE_URL;
import static technology.moro.thesis.Constants.EMAIL_KEY;
import static technology.moro.thesis.Constants.JSON_MEDIA_TYPE;
import static technology.moro.thesis.Constants.JWT_TOKEN_KEY;
import static technology.moro.thesis.Constants.PREF_NAME;
import static technology.moro.thesis.Constants.REPORT_URL;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import technology.moro.thesis.R;
import technology.moro.thesis.dtos.ReportDTO;
import technology.moro.thesis.fragments.MapFragment;
import timber.log.Timber;

public class ReportIncidentActivity extends AppCompatActivity {
    private static final String TAG = "!===== ReportIncidentActivity =====!";

    private Spinner severitySpinner;
    private EditText descriptionEditText;
    private Button reportButton;

    private SharedPreferences sharedPreferences;
    private OkHttpClient httpClient;

    private LatLng userLocation;
    private BroadcastReceiver locationUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_incident);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragment_container_view, MapFragment.class, null)
                .commit();

        severitySpinner = findViewById(R.id.severity_spinner);
        descriptionEditText = findViewById(R.id.description_edit_text);
        reportButton = findViewById(R.id.report_button);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        httpClient = new OkHttpClient();

        ArrayAdapter<CharSequence> severityAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.severity_array,
                android.R.layout.simple_spinner_item
        );
        severityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        severitySpinner.setAdapter(severityAdapter);

        reportButton.setEnabled(false);

        reportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String severity = severitySpinner.getSelectedItem().toString();
                String description = descriptionEditText.getText().toString().trim();

                reportIncident(severity, description);
            }
        });

        locationUpdateReceiver = new LocationUpdateReceiver();
        IntentFilter filter = new IntentFilter("map_location_update");
        LocalBroadcastManager.getInstance(this).registerReceiver(locationUpdateReceiver, filter);
    }

    private void reportIncident(String severity, String description) {
        String jwtToken = sharedPreferences.getString(JWT_TOKEN_KEY, "");
        String email = sharedPreferences.getString(EMAIL_KEY, "");

        ReportDTO report = new ReportDTO(email, severity.toUpperCase(), description, (float) userLocation.latitude, (float) userLocation.longitude);
        RequestBody body = RequestBody.create(report.toJsonString(), JSON_MEDIA_TYPE);

        Request request = new Request.Builder()
                .url(BASE_URL + REPORT_URL)
                .post(body)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Timber.tag(TAG).e("Request failed: %s", e.getMessage());
                showToast("Failed to report incident");
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    showToast("Incident reported successfully");
                } else {
                    showToast("Failed to report incident");
                }
                response.close();
                navigateToHome();
            }
        });
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast t = Toast.makeText(ReportIncidentActivity.this, message, Toast.LENGTH_LONG);
                t.show();
            }
        });
    }

    private void navigateToHome() {
        Intent homeIntent = new Intent(ReportIncidentActivity.this, HomeActivity.class);
        startActivity(homeIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        // Unregister the location update receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationUpdateReceiver);
        super.onDestroy();
    }

    private class LocationUpdateReceiver extends BroadcastReceiver {
        @SuppressLint("SetTextI18n")
        @Override
        public void onReceive(Context context, Intent intent) {
            double latitude = intent.getDoubleExtra("latitude", 0.0);
            double longitude = intent.getDoubleExtra("longitude", 0.0);
            userLocation = new LatLng(latitude, longitude);
            reportButton.setEnabled(true);
        }
    }
}
