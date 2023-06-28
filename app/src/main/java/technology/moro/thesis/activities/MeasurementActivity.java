package technology.moro.thesis.activities;

import static com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY;
import static technology.moro.thesis.Constants.EMAIL_KEY;
import static technology.moro.thesis.Constants.PREF_NAME;
import static technology.moro.thesis.Constants.REQUEST_LOCATION_PERMISSION;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnTokenCanceledListener;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.List;

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;
import technology.moro.thesis.R;
import technology.moro.thesis.dtos.TransmissionDataDTO;
import technology.moro.thesis.services.MeasurementService;
import timber.log.Timber;

public class MeasurementActivity extends AppCompatActivity implements OnMapReadyCallback, SensorEventListener {
    private static final String TAG = "!===== MeasurementActivity =====!";

    private String email;

    private GoogleMap map;
    private LatLng currentLocation;
    private BroadcastReceiver locationUpdateReceiver;

    private TextView elapsedTimeTextView;
    private Handler elapsedTimeHandler;
    private Button startButton;
    private Button stopButton;
    private boolean isMeasuring = false;
    private int elapsedSeconds;

    private static final long ACCELEROMETER_UPDATE_INTERVAL = 50; // 50 milliseconds
    private TextView accelerometerValuesTextView;
    private Handler accelerometerHandler;
    private Sensor accelerometerSensor;
    private SensorManager sensorManager;
    private final float[] accelerometerValues = new float[]{0, 0, 0};

    private MqttAndroidClient mqttClient;
    private static final long MESSAGE_SEND_INTERVAL = 3000; // 3 seconds
    private Handler messageHandler;
    private final List<TransmissionDataDTO> data = new ArrayList<>();

    private static final String CHANNEL_ID = "measurement_activity_channel";
    private static final String CHANNEL_NAME = "Measurement Activity Channel";
    private static final int NOTIFICATION_THRESHOLD = 1800; // 1800 seconds = 30 minutes
    private static int notificationToBeSent;

    @SuppressLint("LongLogTag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement);

        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        email = sharedPreferences.getString(EMAIL_KEY, "");

        // Check if location permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            moveCameraToUserLocation();
        } else {
            // Request location permission from the user
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }

        locationUpdateReceiver = new LocationUpdateReceiver();
        IntentFilter filter = new IntentFilter("location_update");
        LocalBroadcastManager.getInstance(this).registerReceiver(locationUpdateReceiver, filter);

        connectToMqttBroker();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        elapsedTimeTextView = findViewById(R.id.elapsedTimeTextView);
        accelerometerValuesTextView = findViewById(R.id.accelerometerValuesTextView);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        startButton.setEnabled(false);
        stopButton.setEnabled(false);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMeasurement();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMeasurement();
            }
        });

        notificationToBeSent = NOTIFICATION_THRESHOLD;
        createNotificationChannel();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Location permission granted
                moveCameraToUserLocation();
            } else {
                // Location permission denied
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // Show an explanation to the user and request again if needed
                    showPermissionRationaleDialog();
                } else {
                    // User has permanently denied the permission, navigate back to the previous activity or exit the app
                    showPermissionDeniedDialog();
                }
            }
        }
    }

    // Move camera to user's location
    private void moveCameraToUserLocation() {
        initMap();
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY, new CancellationToken() {
                    @NonNull
                    @Override
                    public CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener onTokenCanceledListener) {
                        return new CancellationTokenSource().getToken();
                    }

                    @Override
                    public boolean isCancellationRequested() {
                        return false;
                    }
                })
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            // Update the map camera to the user's current location
                            currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
                            if (!isMeasuring) {
                                startButton.setEnabled(true);
                            }
                        }
                    }
                });
    }

    // Show a dialog explaining why the location permission is required
    private void showPermissionRationaleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Location Permission")
                .setMessage("This app requires access to your location to provide accurate results.")
                .setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Request location permission again
                        ActivityCompat.requestPermissions(MeasurementActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    // Navigate to the previous activity or exit the app
    private void navigateToPreviousActivity() {
        // Here, you can navigate back to the previous activity or close the app as per your requirement
        // For example, you can use the finish() method to close the current activity
        finish();
    }

    @SuppressLint("LongLogTag")
    private void startMeasurement() {
        Timber.tag(TAG).v("Starting measurements...");
        isMeasuring = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        startTimerUpdates();
        startAccelerometerUpdates();
        startMessageSending();
        startForegroundMeasurementService();
        Timber.tag(TAG).v("Measurements started!");
    }

    @SuppressLint("LongLogTag")
    private void stopMeasurement() {
        Timber.tag(TAG).v("Stopping measurements...");
        isMeasuring = false;
        elapsedSeconds = 0;
        data.clear();
        notificationToBeSent = NOTIFICATION_THRESHOLD;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        stopTimerUpdates();
        stopAccelerometerUpdates();
        stopMessageSending();
        stopForegroundMeasurementService();
        resetValues();
        Timber.tag(TAG).v("Measurements stopped!");
    }

    @SuppressLint("LongLogTag")
    private void startMessageSending() {
        Timber.tag(TAG).v("Starting messages transmission...");
        messageHandler = new Handler();
        messageHandler.postDelayed(messageRunnable, MESSAGE_SEND_INTERVAL);
        Timber.tag(TAG).v("Messages transmission started!");
    }

    @SuppressLint("LongLogTag")
    private void stopMessageSending() {
        Timber.tag(TAG).v("Stopping messages transmission...");
        if (messageHandler != null) {
            messageHandler.removeCallbacks(messageRunnable);
            Timber.tag(TAG).v("Messages transmission stopped!");
        }
    }

    private final Runnable messageRunnable = new Runnable() {
        @Override
        public void run() {
            // Send the message using MQTT
            publishMqttMessage(data.toString());
            data.clear();

            if (isMeasuring) {
                messageHandler.postDelayed(this, MESSAGE_SEND_INTERVAL);
            }
        }
    };

    @SuppressLint("LongLogTag")
    private void publishMqttMessage(String payload) {
        Timber.tag(TAG).v("Publishing mqtt message to broker...");
        String topic = "measurements";
        mqttClient.publish(topic, payload.getBytes(), 1, true);
        Timber.tag(TAG).v("Mqtt message published!");
    }

    @SuppressLint("SetTextI18n")
    private void resetValues() {
        elapsedTimeTextView.setText("Elapsed Time: 00:00:00");
        accelerometerValuesTextView.setText("X: 0.0\nY: 0.0\nZ: 0.0");
    }

    private final Runnable accelerometerRunnable = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            float x = accelerometerValues[0];
            float y = accelerometerValues[1];
            float z = accelerometerValues[2];
            accelerometerValuesTextView.setText("X: " + x + "\nY: " + y + "\nZ: " + z);
            data.add(new TransmissionDataDTO(System.currentTimeMillis(), email, (float) currentLocation.longitude, (float) currentLocation.latitude, x, y, z));
            if (isMeasuring) {
                accelerometerHandler.postDelayed(this, ACCELEROMETER_UPDATE_INTERVAL);
            }
        }
    };

    private final Runnable timerRunnable = new Runnable() {
        @SuppressLint({"DefaultLocale", "SetTextI18n"})
        @Override
        public void run() {
            int seconds = elapsedSeconds % 60;
            int minutes = elapsedSeconds / 60;
            int hours = elapsedSeconds / 3600;

            elapsedTimeTextView.setText("Elapsed Time: " + String.format("%02d", hours) + ":" + String.format("%02d", minutes) + ":" + String.format("%02d", seconds));

            if (isMeasuring) {
                elapsedSeconds++;
                // Schedule and send a notification every half an hour
                if (elapsedSeconds > notificationToBeSent) {
                    sendNotification();
                    notificationToBeSent += NOTIFICATION_THRESHOLD;
                }
                elapsedTimeHandler.postDelayed(this, 1000);
            }
        }
    };

    @SuppressLint("LongLogTag")
    private void startAccelerometerUpdates() {
        Timber.tag(TAG).v("Starting accelerometer updates...");
        accelerometerHandler = new Handler();
        accelerometerHandler.post(accelerometerRunnable);
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        Timber.tag(TAG).v("Accelerometer updates started!");
    }

    @SuppressLint("LongLogTag")
    private void stopAccelerometerUpdates() {
        Timber.tag(TAG).v("Stopping accelerometer updates...");
        if (accelerometerHandler != null) {
            accelerometerHandler.removeCallbacks(accelerometerRunnable);
            sensorManager.unregisterListener(this);
            Timber.tag(TAG).v("Accelerometer updates stopped!");
        }
    }

    @SuppressLint("LongLogTag")
    private void startTimerUpdates() {
        Timber.tag(TAG).v("Starting accelerometer updates...");
        elapsedTimeHandler = new Handler();
        elapsedTimeHandler.post(timerRunnable);
        Timber.tag(TAG).v("Accelerometer updates started!");
    }

    @SuppressLint("LongLogTag")
    private void stopTimerUpdates() {
        Timber.tag(TAG).v("Stopping timer updates...");
        if (elapsedTimeHandler != null) {
            elapsedTimeHandler.removeCallbacks(timerRunnable);
            Timber.tag(TAG).v("Timer updates stopped!");
        }
    }

    @SuppressLint("LongLogTag")
    private void connectToMqttBroker() {
        Timber.tag(TAG).v("Trying to connect to mqtt broker...");
        String mqttBrokerUrl = "ssl://f0cdbc9159594b919d68036f1fc85241.s2.eu.hivemq.cloud:8883";
        String username = "UPMFinalThesis2023";
        String password = "UPMFinalThesis2023";

        mqttClient = new MqttAndroidClient(getApplicationContext(), mqttBrokerUrl, MqttAsyncClient.generateClientId(), Ack.AUTO_ACK);

        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    Timber.tag(TAG).v("Reconnected");
                } else {
                    Timber.tag(TAG).v("Connected");
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                Timber.tag(TAG).v("The mqtt connection lost!");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                Timber.tag(TAG).v("Mqtt message arrived!");
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Timber.tag(TAG).v("Mqtt message delivered!");
            }
        });

        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setUserName(username);
        connectOptions.setPassword(password.toCharArray());
        connectOptions.setAutomaticReconnect(true);

        mqttClient.connect(connectOptions, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Timber.tag(TAG).v("Connect to mqtt broker was successful!");
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Timber.tag(TAG).v("Connect to mqtt broker failed!");
                exception.printStackTrace();
            }
        });
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_view_measurement);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;

        // Check if location is enabled on the device
        boolean isLocationEnabled = isLocationEnabled();
        if (!isLocationEnabled) {
            // Location is disabled, show a toast message and navigate back to HomeActivity
            showToast("Location is disabled. Map cannot be used.");
            navigateToHomeActivity();
            return;
        }

        // Enable the user's current location on the map
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);
        }
    }

    private void navigateToHomeActivity() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean isLocationEnabled() {
        int mode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        return (mode != Settings.Secure.LOCATION_MODE_OFF);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onSensorChanged(SensorEvent event) {
        accelerometerValues[0] = event.values[0];
        accelerometerValues[1] = event.values[1];
        accelerometerValues[2] = event.values[2];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.enableLights(true);
        channel.setLightColor(Color.RED);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private void sendNotification() {
        Timber.tag(TAG).v("Sending notification...");
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Did you reach your destination?")
                .setContentText("If yes, please stop the measurement or close the app!")
                .setSmallIcon(R.drawable.ramp_logo)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        // Send the notification
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(0, notification);
        Timber.tag(TAG).v("Notification sent!");
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast t = Toast.makeText(MeasurementActivity.this, message, Toast.LENGTH_SHORT);
                t.setGravity(Gravity.FILL_HORIZONTAL, 0, 0);
                t.show();
            }
        });
    }

    private void startForegroundMeasurementService() {
        Intent serviceIntent = new Intent(this, MeasurementService.class);
        serviceIntent.setAction("START_FOREGROUND_ACTION");
        startForegroundService(serviceIntent);
    }

    private void stopForegroundMeasurementService() {
        Intent serviceIntent = new Intent(this, MeasurementService.class);
        serviceIntent.setAction("STOP_FOREGROUND_ACTION");
        startForegroundService(serviceIntent);
    }

    @Override
    protected void onDestroy() {
        // Unregister the location update receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationUpdateReceiver);
        super.onDestroy();
    }

    private class LocationUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            double latitude = intent.getDoubleExtra("latitude", 0.0);
            double longitude = intent.getDoubleExtra("longitude", 0.0);
            currentLocation = new LatLng(latitude, longitude);
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
            if (!isMeasuring) {
                startButton.setEnabled(true);
            }
        }
    }
}
