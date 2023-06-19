package technology.moro.thesis.activities;

import static technology.moro.thesis.Constants.BASE_URL;
import static technology.moro.thesis.Constants.DATE_FORMAT;
import static technology.moro.thesis.Constants.EMAIL_KEY;
import static technology.moro.thesis.Constants.GET_REPORTS_URL;
import static technology.moro.thesis.Constants.GET_STREET_INFO_URL;
import static technology.moro.thesis.Constants.JWT_TOKEN_KEY;
import static technology.moro.thesis.Constants.PREF_NAME;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import technology.moro.thesis.dtos.Incident;
import technology.moro.thesis.R;
import technology.moro.thesis.dtos.StreetInfo;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnCameraChangeListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private SharedPreferences sharedPreferences;
    private OkHttpClient httpClient;

    private String jwtToken;
    private String email;

    @SuppressLint("SimpleDateFormat")
    SimpleDateFormat dt = new SimpleDateFormat(DATE_FORMAT);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        httpClient = new OkHttpClient();

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Location permission granted, proceed with map initialization
                initMap();
            } else {
                showToast("Location permission not granted. Map cannot be used.");
                navigateToHomeActivity();
            }
        }
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_view);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnCameraChangeListener(this);

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

        jwtToken = sharedPreferences.getString(JWT_TOKEN_KEY, "");
        email = sharedPreferences.getString(EMAIL_KEY, "");
        // Perform the GET requests for incidents and streets
        performGetIncidentsRequest(email, jwtToken);
        performGetStreetRequest(email, jwtToken);
    }

    private void performGetIncidentsRequest(String email, String jwtToken) {
        LatLngBounds boundingBox = getMapBoundingBox();
        double swlat = boundingBox.southwest.latitude;
        double swlon = boundingBox.southwest.longitude;
        double nelat = boundingBox.northeast.latitude;
        double nelon = boundingBox.northeast.longitude;

        String params = "?email=" + email + "&minLatitude=" + swlat + "&maxLatitude=" + nelat +
                "&minLongitude=" + swlon + "&maxLongitude=" + nelon;

        Request request = new Request.Builder()
                .url(BASE_URL + GET_REPORTS_URL + params)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                showToast("Failed to fetch incidents");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    // Parse the response data and extract the incident information
                    List<Incident> incidents = parseIncidents(responseData);

                    // Add markers to the map for each incident
                    runOnUiThread(() -> addIncidentMarkers(incidents));
                } else {
                    showToast("Failed to fetch incidents");
                }
            }
        });
    }

    private void performGetStreetRequest(String email, String jwtToken) {
        LatLngBounds boundingBox = getMapBoundingBox();
        double swlat = boundingBox.southwest.latitude;
        double swlon = boundingBox.southwest.longitude;
        double nelat = boundingBox.northeast.latitude;
        double nelon = boundingBox.northeast.longitude;

        String params = "?email=" + email + "&minLatitude=" + swlat + "&maxLatitude=" + nelat +
                "&minLongitude=" + swlon + "&maxLongitude=" + nelon;

        Request request = new Request.Builder()
                .url(BASE_URL + GET_STREET_INFO_URL + params)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                showToast("Failed to fetch street info");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    // Parse the response data and extract the street information
                    List<StreetInfo> streets = parseStreets(responseData);

                    // Add markers to the map for each street
                    runOnUiThread(() -> addStreetMarkers(streets));
                } else {
                    showToast("Failed to fetch street info");
                }
            }
        });
    }

    private List<Incident> parseIncidents(String responseData) {
        // Parse the JSON response data and create a list of Incident objects
        List<Incident> incidents = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(responseData);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonIncident = jsonArray.getJSONObject(i);

                Long reportId = jsonIncident.getLong("reportId");
                String userEmail = jsonIncident.getString("userEmail");
                String severity = jsonIncident.getString("severity");
                String description = jsonIncident.getString("description");
                float latitude = (float) jsonIncident.getDouble("latitude");
                float longitude = (float) jsonIncident.getDouble("longitude");
                Date reportDate = dt.parse(jsonIncident.getString("reportDate"));

                Incident incident = new Incident(reportId, userEmail, severity, description, latitude, longitude, reportDate);
                incidents.add(incident);
            }
        } catch (JSONException | ParseException e) {
            e.printStackTrace();
        }

        return incidents;
    }

    private List<StreetInfo> parseStreets(String responseData) {
        List<StreetInfo> streets = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(responseData);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonStreet = jsonArray.getJSONObject(i);

                String severity = jsonStreet.getString("severity");
                float latitude = (float) jsonStreet.getDouble("latitude");
                float longitude = (float) jsonStreet.getDouble("longitude");

                StreetInfo street = new StreetInfo(severity, latitude, longitude);
                streets.add(street);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return streets;
    }

    private void addIncidentMarkers(List<Incident> incidents) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        for (Incident incident : incidents) {
            LatLng latLng = new LatLng(incident.getLatitude(), incident.getLongitude());
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title(dateFormat.format(incident.getReportDate()))
                    .snippet(incident.getDescription());

            // Set marker icon based on severity
            switch (incident.getSeverity()) {
                case "HIGH":
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                    break;
                case "MEDIUM":
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
                    break;
                case "LOW":
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
                    break;
            }

            mMap.addMarker(markerOptions);
        }
    }

    private void addStreetMarkers(List<StreetInfo> streets) {

        Bitmap redDot = getBitmap(R.drawable.red_dot);
        Bitmap orangeDot = getBitmap(R.drawable.orange_dot);
        Bitmap yellowDot = getBitmap(R.drawable.yellow_dot);

        for (StreetInfo street : streets) {
            LatLng latLng = new LatLng(street.getLatitude(), street.getLongitude());
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng);

            // Set marker icon based on severity
            switch (street.getSeverity()) {
                case "HIGH":
                    markerOptions.icon(BitmapDescriptorFactory.fromBitmap(redDot));
                    break;
                case "MEDIUM":
                    markerOptions.icon(BitmapDescriptorFactory.fromBitmap(orangeDot));
                    break;
                case "LOW":
                    markerOptions.icon(BitmapDescriptorFactory.fromBitmap(yellowDot));
                    break;
            }

            mMap.addMarker(markerOptions);
        }
    }

    private Bitmap getBitmap(int drawableRes) {
        Drawable drawable = getResources().getDrawable(drawableRes);
        Canvas canvas = new Canvas();
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private boolean isLocationEnabled() {
        int mode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF);
        return (mode != Settings.Secure.LOCATION_MODE_OFF);
    }

    private void startLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
//        locationRequest.setInterval(2000); // Update every 2 seconds

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {

                for (Location location : locationResult.getLocations()) {
                    // Update the map camera to the user's current location
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private LatLngBounds getMapBoundingBox() {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        VisibleRegion visibleRegion = mMap.getProjection().getVisibleRegion();

        builder.include(visibleRegion.nearLeft);   // Bottom-left corner
        builder.include(visibleRegion.nearRight);  // Bottom-right corner
        builder.include(visibleRegion.farLeft);    // Top-left corner
        builder.include(visibleRegion.farRight);   // Top-right corner

        return builder.build();
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

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast t = Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT);
                t.setGravity(Gravity.FILL_HORIZONTAL, 0, 0);
                t.show();
            }
        });
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        mMap.clear();
        performGetIncidentsRequest(email, jwtToken);
        performGetStreetRequest(email, jwtToken);
    }
}
