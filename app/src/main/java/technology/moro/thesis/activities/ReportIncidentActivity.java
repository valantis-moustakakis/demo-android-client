package technology.moro.thesis.activities;

import static technology.moro.thesis.Constants.BASE_URL;
import static technology.moro.thesis.Constants.EMAIL_KEY;
import static technology.moro.thesis.Constants.JSON_MEDIA_TYPE;
import static technology.moro.thesis.Constants.JWT_TOKEN_KEY;
import static technology.moro.thesis.Constants.PREF_NAME;
import static technology.moro.thesis.Constants.REPORT_URL;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import technology.moro.thesis.R;
import technology.moro.thesis.dtos.ReportDTO;

public class ReportIncidentActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int REQUEST_LOCATION_PERMISSION = 1;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private Spinner severitySpinner;
    private EditText descriptionEditText;
    private Button reportButton;

    private SharedPreferences sharedPreferences;
    private OkHttpClient httpClient;

    private LatLng userLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_incident);

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

        reportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String severity = severitySpinner.getSelectedItem().toString();
                String description = descriptionEditText.getText().toString().trim();

                reportIncident(severity, description);
            }
        });

        // Check if location permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        } else {
            // Permission is already granted, proceed with map initialization
            initMap();
        }
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
                Log.e("ReportIncidentActivity", "Request failed: " + e.getMessage());
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
                Toast t = Toast.makeText(ReportIncidentActivity.this, message, Toast.LENGTH_SHORT);
                t.setGravity(Gravity.FILL_HORIZONTAL, 0, 0);
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Location permission granted, proceed with map initialization
                initMap();
            } else {
                // Location permission not granted, show a toast message and navigate back to HomeActivity
                Toast.makeText(this, "Location permission not granted. Map cannot be used.",
                        Toast.LENGTH_SHORT).show();
                navigateToHomeActivity();
            }
        }
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_view_incident);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check if location is enabled on the device
        boolean isLocationEnabled = isLocationEnabled();
        if (!isLocationEnabled) {
            // Location is disabled, show a toast message and navigate back to HomeActivity
            Toast.makeText(this, "Location is disabled. Map cannot be used.",
                    Toast.LENGTH_SHORT).show();
            navigateToHomeActivity();
            return;
        }

        // Enable the user's current location on the map
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        // Start location updates
        startLocationUpdates();
    }

    private boolean isLocationEnabled() {
        int mode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF);
        return (mode != Settings.Secure.LOCATION_MODE_OFF);
    }

    private void startLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest locationRequest = LocationRequest.create();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    // Update the map camera to the user's current location
                    userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f));
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void navigateToHomeActivity() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
    }
}
