package technology.moro.thesis.activities;

import static technology.moro.thesis.Constants.EMAIL_KEY;
import static technology.moro.thesis.Constants.PREF_NAME;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
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

public class MeasurementActivity extends AppCompatActivity implements OnMapReadyCallback, SensorEventListener {
    private static final String TAG = "!===== MeasurementActivity =====!";

    private SharedPreferences sharedPreferences;
    private String email;

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private static final long LOCATION_UPDATE_INTERVAL = 3000; // 3 seconds
    private LatLng currentLocation;

    private TextView elapsedTimeTextView;
    private Handler elapsedTimeHandler;
    private Button startButton;
    private Button stopButton;
    private boolean isMeasuring = false;
    private int elapsedSeconds;

    private static final long ACCELEROMETER_UPDATE_INTERVAL = 100; // 200 milliseconds
    private TextView accelerometerValuesTextView;
    private Handler accelerometerHandler;
    private Sensor accelerometerSensor;
    private SensorManager sensorManager;
    private final float[] accelerometerValues = new float[]{0,0,0};

    private MqttAndroidClient mqttClient;
    private static final long MESSAGE_SEND_INTERVAL = 3000; // 6 seconds
    private Handler messageHandler;
    private final List<TransmissionDataDTO> data = new ArrayList<>();

    private static final String CHANNEL_ID = "measurement_notification_channel";
    private static final String CHANNEL_NAME = "Measurement Notification Channel";
    private static final int NOTIFICATION_THRESHOLD = 10; // value in seconds -> 1800 = 30 minutes
    private static int notificationToBeSent;

    @SuppressLint("LongLogTag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "======================================= MeasurementActivity ===============================================================");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        email = sharedPreferences.getString(EMAIL_KEY, "");

        // Check if location permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
            initMap();
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setInterval(LOCATION_UPDATE_INTERVAL);
            locationRequest.setFastestInterval(LOCATION_UPDATE_INTERVAL);
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    for (Location location : locationResult.getLocations()) {
                        // Update the map camera to the user's current location
                        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
                        if (!isMeasuring) {
                            startButton.setEnabled(true);
                        }
                    }
                }
            };

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }

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

    @SuppressLint("LongLogTag")
    private void startMeasurement() {
        Log.v(TAG, "Starting measurements...");
        isMeasuring = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        startTimerUpdates();
        startAccelerometerUpdates();
        startMessageSending();
        Log.v(TAG, "Measurements started!");
    }

    @SuppressLint("LongLogTag")
    private void stopMeasurement() {
        Log.v(TAG, "Stopping measurements...");
        isMeasuring = false;
        elapsedSeconds = 0;
        data.clear();
        notificationToBeSent = NOTIFICATION_THRESHOLD;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        stopLocationUpdates();
        stopTimerUpdates();
        stopAccelerometerUpdates();
        stopMessageSending();
        resetValues();
        Log.v(TAG, "Measurements stopped!");
    }

    @SuppressLint("LongLogTag")
    private void startMessageSending() {
        Log.v(TAG, "Starting messages transmission...");
        messageHandler = new Handler();
        messageHandler.postDelayed(messageRunnable, MESSAGE_SEND_INTERVAL);
        Log.v(TAG, "Messages transmission started!");
    }

    @SuppressLint("LongLogTag")
    private void stopMessageSending() {
        Log.v(TAG, "Stopping messages transmission...");
        if (messageHandler != null) {
            messageHandler.removeCallbacks(messageRunnable);
            Log.v(TAG, "Messages transmission stopped!");
        }
    }

    private Runnable messageRunnable = new Runnable() {
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
        Log.v(TAG, "Publishing mqtt message to broker...");
        String topic = "measurements";
        mqttClient.publish(topic, payload.getBytes(), 1, true);
        Log.v(TAG, "Mqtt message published!");
    }

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
            accelerometerValuesTextView.setText("X: " + x + "\nY: " + y + "\nZ: " + x);
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
        Log.v(TAG, "Starting accelerometer updates...");
        accelerometerHandler = new Handler();
        accelerometerHandler.post(accelerometerRunnable);
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        Log.v(TAG, "Accelerometer updates started!");
    }

    @SuppressLint("LongLogTag")
    private void stopAccelerometerUpdates() {
        Log.v(TAG, "Stopping accelerometer updates...");
        if (accelerometerHandler != null) {
            accelerometerHandler.removeCallbacks(accelerometerRunnable);
            sensorManager.unregisterListener(this);
            Log.v(TAG, "Accelerometer updates stopped!");
        }
    }

    @SuppressLint("LongLogTag")
    private void startTimerUpdates() {
        Log.v(TAG, "Starting accelerometer updates...");
        elapsedTimeHandler = new Handler();
        elapsedTimeHandler.post(timerRunnable);
        Log.v(TAG, "Accelerometer updates started!");
    }

    @SuppressLint("LongLogTag")
    private void stopTimerUpdates() {
        Log.v(TAG, "Stopping timer updates...");
        if (elapsedTimeHandler != null) {
            elapsedTimeHandler.removeCallbacks(timerRunnable);
            Log.v(TAG, "Timer updates stopped!");
        }
    }

    @SuppressLint("LongLogTag")
    private void stopLocationUpdates() {
        Log.v(TAG, "Stopping location updates...");
        fusedLocationClient.removeLocationUpdates(locationCallback);
        Log.v(TAG, "Location updates stopped!");
    }

    @SuppressLint("LongLogTag")
    private void connectToMqttBroker() {
        Log.v(TAG, "Trying to connect to mqtt broker...");
        String mqttBrokerUrl = "ssl://f0cdbc9159594b919d68036f1fc85241.s2.eu.hivemq.cloud:8883";
        String username = "UPMFinalThesis2023";
        String password = "UPMFinalThesis2023";

        mqttClient = new MqttAndroidClient(getApplicationContext(), mqttBrokerUrl, MqttAsyncClient.generateClientId(), Ack.AUTO_ACK);

        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    Log.v(TAG, "Reconnected");
                } else {
                    Log.v(TAG, "Connected");
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.v(TAG, "The mqtt connection lost!");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                Log.v(TAG, "Mqtt message arrived!");
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.v(TAG, "Mqtt message delivered!");
            }
        });

        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setUserName(username);
        connectOptions.setPassword(password.toCharArray());
        connectOptions.setAutomaticReconnect(true);

        mqttClient.connect(connectOptions, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Log.v(TAG, "Connect to mqtt broker was successful!");
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.v(TAG, "Connect to mqtt broker failed!");
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
        int mode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF);
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
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("My Channel Description");
        channel.enableLights(true);
        channel.setLightColor(Color.RED);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private void sendNotification() {
        Log.v(TAG, "Sending notification...");
        // Create an intent for opening the MainActivity when the notification is clicked
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Did you reach your destination?")
                .setContentText("If yes, please stop the measurement or close the app!")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        // Send the notification
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(0, notification);
        Log.v(TAG, "Notification sent!");
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
}
