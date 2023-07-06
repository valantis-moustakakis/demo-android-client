package technology.moro.thesis.activities;

import static technology.moro.thesis.Constants.ACCURACY;
import static technology.moro.thesis.Constants.EMAIL_KEY;
import static technology.moro.thesis.Constants.LATITUDE;
import static technology.moro.thesis.Constants.LONGITUDE;
import static technology.moro.thesis.Constants.MQTT_BROKER_PASSWORD;
import static technology.moro.thesis.Constants.MQTT_BROKER_TOPIC;
import static technology.moro.thesis.Constants.MQTT_BROKER_URL;
import static technology.moro.thesis.Constants.MQTT_BROKER_USERNAME;
import static technology.moro.thesis.Constants.PREF_NAME;
import static technology.moro.thesis.Constants.START_FOREGROUND_ACTION;
import static technology.moro.thesis.Constants.STOP_FOREGROUND_ACTION;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
import technology.moro.thesis.fragments.MapFragment;
import technology.moro.thesis.services.MeasurementService;

public class MeasurementActivity extends AppCompatActivity implements SensorEventListener {
    private String email;

    private LatLng currentLocation;
    private BroadcastReceiver backgroundLocationUpdateReceiver;
    private BroadcastReceiver locationUpdateReceiver;
    private IntentFilter locationUpdateReceiverFilter;

    private TextView elapsedTimeTextView;
    private TextView gpsSignalTextView;
    private Handler elapsedTimeHandler;
    private Button startButton;
    private Button stopButton;
    private boolean isMeasuring = false;
    private int elapsedSeconds;

    private static final long ACCELEROMETER_UPDATE_INTERVAL = 50; // 50 milliseconds
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
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragment_container_view, MapFragment.class, null)
                .commit();

        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        email = sharedPreferences.getString(EMAIL_KEY, "");

        backgroundLocationUpdateReceiver = new LocationUpdateReceiver();
        IntentFilter backgroundLocationUpdateReceiverFilter = new IntentFilter("location_update");
        LocalBroadcastManager.getInstance(this).registerReceiver(backgroundLocationUpdateReceiver, backgroundLocationUpdateReceiverFilter);
        locationUpdateReceiver = new LocationUpdateReceiver();
        locationUpdateReceiverFilter = new IntentFilter("map_location_update");
        LocalBroadcastManager.getInstance(this).registerReceiver(locationUpdateReceiver, locationUpdateReceiverFilter);

        connectToMqttBroker();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        elapsedTimeTextView = findViewById(R.id.elapsedTimeTextView);
        gpsSignalTextView = findViewById(R.id.gpsSignalTextView);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        disableButton(startButton);
        disableButton(stopButton);

        startButton.setOnClickListener(v -> startMeasurement());
        stopButton.setOnClickListener(v -> stopMeasurement());

        notificationToBeSent = NOTIFICATION_THRESHOLD;
        createNotificationChannel();
    }

    @SuppressLint("LongLogTag")
    private void startMeasurement() {
        isMeasuring = true;
        disableButton(startButton);
        enableButton(stopButton);
        startTimerUpdates();
        startAccelerometerUpdates();
        startMessageSending();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationUpdateReceiver);
        startForegroundMeasurementService();
    }

    @SuppressLint("LongLogTag")
    private void stopMeasurement() {
        isMeasuring = false;
        elapsedSeconds = 0;
        data.clear();
        notificationToBeSent = NOTIFICATION_THRESHOLD;
        disableButton(stopButton);
        enableButton(startButton);
        stopTimerUpdates();
        stopAccelerometerUpdates();
        stopMessageSending();
        stopForegroundMeasurementService();
        LocalBroadcastManager.getInstance(this).registerReceiver(locationUpdateReceiver, locationUpdateReceiverFilter);
        resetValues();
    }

    @SuppressLint("LongLogTag")
    private void startMessageSending() {
        messageHandler = new Handler();
        messageHandler.postDelayed(messageRunnable, MESSAGE_SEND_INTERVAL);
    }

    @SuppressLint("LongLogTag")
    private void stopMessageSending() {
        if (messageHandler != null) {
            messageHandler.removeCallbacks(messageRunnable);
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
        mqttClient.publish(MQTT_BROKER_TOPIC, payload.getBytes(), 1, true);
    }

    @SuppressLint("SetTextI18n")
    private void resetValues() {
        elapsedTimeTextView.setText(getString(R.string.start_elapsed_time));
    }

    private final Runnable accelerometerRunnable = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            float x = accelerometerValues[0];
            float y = accelerometerValues[1];
            float z = accelerometerValues[2];
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
        accelerometerHandler = new Handler();
        accelerometerHandler.post(accelerometerRunnable);
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @SuppressLint("LongLogTag")
    private void stopAccelerometerUpdates() {
        if (accelerometerHandler != null) {
            accelerometerHandler.removeCallbacks(accelerometerRunnable);
            sensorManager.unregisterListener(this);
        }
    }

    @SuppressLint("LongLogTag")
    private void startTimerUpdates() {
        elapsedTimeHandler = new Handler();
        elapsedTimeHandler.post(timerRunnable);
    }

    @SuppressLint("LongLogTag")
    private void stopTimerUpdates() {
        if (elapsedTimeHandler != null) {
            elapsedTimeHandler.removeCallbacks(timerRunnable);
        }
    }

    @SuppressLint("LongLogTag")
    private void connectToMqttBroker() {

        mqttClient = new MqttAndroidClient(getApplicationContext(), MQTT_BROKER_URL, MqttAsyncClient.generateClientId(), Ack.AUTO_ACK);

        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
            }

            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setUserName(MQTT_BROKER_USERNAME);
        connectOptions.setPassword(MQTT_BROKER_PASSWORD.toCharArray());
        connectOptions.setAutomaticReconnect(true);

        mqttClient.connect(connectOptions, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                exception.printStackTrace();
            }
        });
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
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.did_you_reach_your_destination))
                .setContentText(getString(R.string.stop_measurement_message))
                .setSmallIcon(R.drawable.ramp_logo)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        // Send the notification
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(0, notification);
    }

    private void startForegroundMeasurementService() {
        Intent serviceIntent = new Intent(this, MeasurementService.class);
        serviceIntent.setAction(START_FOREGROUND_ACTION);
        startForegroundService(serviceIntent);
    }

    private void stopForegroundMeasurementService() {
        Intent serviceIntent = new Intent(this, MeasurementService.class);
        serviceIntent.setAction(STOP_FOREGROUND_ACTION);
        startForegroundService(serviceIntent);
    }

    @Override
    protected void onDestroy() {
        // Unregister the location update receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(backgroundLocationUpdateReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationUpdateReceiver);
        super.onDestroy();
    }

    private void enableButton(Button button) {
        button.setAlpha(1f);
        button.setClickable(true);
    }

    private void disableButton(Button button) {
        button.setAlpha(.25f);
        button.setClickable(false);
    }

    private class LocationUpdateReceiver extends BroadcastReceiver {
        @SuppressLint("SetTextI18n")
        @Override
        public void onReceive(Context context, Intent intent) {
            double latitude = intent.getDoubleExtra(LATITUDE, 0.0);
            double longitude = intent.getDoubleExtra(LONGITUDE, 0.0);
            float accuracy = intent.getFloatExtra(ACCURACY, 0.0f);
            currentLocation = new LatLng(latitude, longitude);

            String gpsSignalPower = accuracy > 7 ? getString(R.string.gps_signal_weak) : getString(R.string.gps_signal_strong);
            gpsSignalTextView.setText(getString(R.string.gps_signal_label) + gpsSignalPower);

            if (!isMeasuring) {
                enableButton(startButton);
                disableButton(stopButton);
            }
        }
    }

}
