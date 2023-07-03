package technology.moro.thesis.fragments;

import static android.content.Context.MODE_PRIVATE;
import static technology.moro.thesis.Constants.BASE_URL;
import static technology.moro.thesis.Constants.DATE_FORMAT;
import static technology.moro.thesis.Constants.EMAIL_KEY;
import static technology.moro.thesis.Constants.GET_REPORTS_URL;
import static technology.moro.thesis.Constants.GET_STREET_INFO_URL;
import static technology.moro.thesis.Constants.JWT_TOKEN_KEY;
import static technology.moro.thesis.Constants.LOCATION_UPDATE_INTERVAL;
import static technology.moro.thesis.Constants.PREF_NAME;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
import com.google.android.gms.maps.model.Marker;
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
import technology.moro.thesis.R;
import technology.moro.thesis.activities.HomeActivity;
import technology.moro.thesis.dtos.Incident;
import technology.moro.thesis.dtos.StreetInfo;

public class MapFragment extends SupportMapFragment implements OnMapReadyCallback, GoogleMap.OnCameraChangeListener, ActivityResultCallback<Boolean> {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean firstLocation = true;

    private Handler cameraChangeHandler;
    private boolean cameraChangeInProgress;

    private SharedPreferences sharedPreferences;
    private OkHttpClient httpClient;

    private String jwtToken;
    private String email;

    @SuppressLint("SimpleDateFormat")
    SimpleDateFormat dt = new SimpleDateFormat(DATE_FORMAT);

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getMapAsync(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = requireActivity().getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        httpClient = new OkHttpClient();

        cameraChangeHandler = new Handler(Looper.getMainLooper());
        cameraChangeInProgress = false;

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), this);

        // Check if location permission is granted
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnCameraChangeListener(this);
        // Create a custom info window adapter
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null; // Return null to use the default info window
            }

            @Override
            public View getInfoContents(Marker marker) {
                // Inflate the custom info window layout
                View view = getLayoutInflater().inflate(R.layout.custom_info_window, null);

                // Find the views in the custom info window layout
                TextView titleTextView = view.findViewById(R.id.titleTextView);
                TextView snippetTextView = view.findViewById(R.id.snippetTextView);

                // Set the title and snippet text
                titleTextView.setText(marker.getTitle());
                snippetTextView.setText(marker.getSnippet());

                // Measure the view to determine its dimensions
                int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                view.measure(widthMeasureSpec, heightMeasureSpec);

                return view;
            }
        });

        // Check if location is enabled on the device
        boolean isLocationEnabled = isLocationEnabled();
        if (!isLocationEnabled) {
            // Location is disabled, show a toast message and navigate back to HomeActivity
            Toast.makeText(requireActivity(), "Location is disabled. Map cannot be used.", Toast.LENGTH_LONG).show();
            navigateToHomeActivity();
            return;
        }

        // Enable the user's current location on the map
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);

            // Start location updates
            startLocationUpdates();

            jwtToken = sharedPreferences.getString(JWT_TOKEN_KEY, "");
            email = sharedPreferences.getString(EMAIL_KEY, "");
            // Perform the GET requests for incidents and streets
            LatLngBounds boundingBox = getMapBoundingBox();
            performGetIncidentsRequest(email, jwtToken, boundingBox);
            performGetStreetRequest(email, jwtToken, boundingBox);
        }
    }

    // Show a dialog explaining why the location permission is required
    private void showPermissionRationaleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Location Permission")
                .setMessage("This app requires access to your location to provide accurate results.")
                .setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Request location permission again
                        ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // User has denied the permission, navigate back to the previous activity or exit the app
                        navigateToPreviousActivity();
                    }
                })
                .setCancelable(false)
                .show();
    }

    // Show a dialog informing the user about denied permission and providing an option to open app settings
    private void showPermissionDeniedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Permission Denied")
                .setMessage("You have denied location permission. To enable this feature, please grant the permission from the app settings.")
                .setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        openAppSettings();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // User has denied the permission, navigate back to the previous activity or exit the app
                        navigateToPreviousActivity();
                    }
                })
                .setCancelable(false)
                .show();
    }

    // Open the app settings screen
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    // Navigate to the previous activity or exit the app
    private void navigateToPreviousActivity() {
        // Here, you can navigate back to the previous activity or close the app as per your requirement
        // For example, you can use the finish() method to close the current activity
        requireActivity().finish();
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) requireActivity().getSupportFragmentManager().findFragmentById(R.id.map_view);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void performGetIncidentsRequest(String email, String jwtToken, LatLngBounds boundingBox) {
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
                    requireActivity().runOnUiThread(() -> addIncidentMarkers(incidents));
                } else {
                    showToast("Failed to fetch incidents");
                }
            }
        });
    }

    private void performGetStreetRequest(String email, String jwtToken, LatLngBounds boundingBox) {
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
                    requireActivity().runOnUiThread(() -> addStreetMarkers(streets));
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
        Bitmap greenDot = getBitmap(R.drawable.green_dot);

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
                case "NO_SEVERITY":
                    markerOptions.icon(BitmapDescriptorFactory.fromBitmap(greenDot));
                    break;
            }

            mMap.addMarker(markerOptions);
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
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
        int mode = Settings.Secure.getInt(requireActivity().getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        return (mode != Settings.Secure.LOCATION_MODE_OFF);
    }

    private void startLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(LOCATION_UPDATE_INTERVAL);
        locationRequest.setFastestInterval(LOCATION_UPDATE_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    // Update the map camera to the user's current location
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    if (firstLocation) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
                        firstLocation = false;
                    }
                    Intent intent = new Intent("map_location_update");
                    intent.putExtra("latitude", location.getLatitude());
                    intent.putExtra("longitude", location.getLongitude());
                    intent.putExtra("accuracy", location.getAccuracy());
                    LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
        Intent intent = new Intent(requireActivity(), HomeActivity.class);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
    }

    private void showToast(final String message) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast t = Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG);
                    t.show();
                }
            });
        }
    }

    @Override
    public void onCameraChange(@NonNull CameraPosition cameraPosition) {
        if (!cameraChangeInProgress && !firstLocation) {
            // If there is no camera change in progress, execute the code immediately
            LatLngBounds boundingBox = getMapBoundingBox();
            performGetIncidentsRequest(email, jwtToken, boundingBox);
            performGetStreetRequest(email, jwtToken, boundingBox);

            // Set a flag to indicate that a camera change is in progress
            cameraChangeInProgress = true;

            // Schedule a delayed task to reset the flag after 1 second
            cameraChangeHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    cameraChangeInProgress = false;
                }
            }, 2000);
        }
    }

    @Override
    public void onActivityResult(Boolean isPermissionGranted) {
        if (isPermissionGranted) {
            // Location permission granted, handle it
            handleLocationPermissionGranted();
        } else {
            // Location permission denied or revoked, handle it
            handleLocationPermissionDenied();
        }
    }

    private void handleLocationPermissionGranted() {
        // Location permission is granted, perform the necessary operations
        initMap();

        // Start location updates
        startLocationUpdates();

        jwtToken = sharedPreferences.getString(JWT_TOKEN_KEY, "");
        email = sharedPreferences.getString(EMAIL_KEY, "");
        // Perform the GET requests for incidents and streets
        LatLngBounds boundingBox = getMapBoundingBox();
        performGetIncidentsRequest(email, jwtToken, boundingBox);
        performGetStreetRequest(email, jwtToken, boundingBox);
    }

    private void handleLocationPermissionDenied() {
        // Location permission is denied or revoked, handle it accordingly
        // Location permission denied
        if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Show an explanation to the user and request again if needed
            showPermissionRationaleDialog();
        } else {
            // User has permanently denied the permission, navigate back to the previous activity or exit the app
            showPermissionDeniedDialog();
        }
    }
}
