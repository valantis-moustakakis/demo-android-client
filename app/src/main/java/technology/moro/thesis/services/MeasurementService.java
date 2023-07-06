package technology.moro.thesis.services;

import static technology.moro.thesis.Constants.ACCURACY;
import static technology.moro.thesis.Constants.LATITUDE;
import static technology.moro.thesis.Constants.LOCATION_UPDATE_INTERVAL;
import static technology.moro.thesis.Constants.LONGITUDE;
import static technology.moro.thesis.Constants.START_FOREGROUND_ACTION;
import static technology.moro.thesis.Constants.STOP_FOREGROUND_ACTION;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import technology.moro.thesis.R;

public class MeasurementService extends Service {
    private static final int NOTIFICATION_ID = 18;
    private static final String CHANNEL_ID = "measurement_service_channel";
    private static final String CHANNEL_NAME = "Measurement Service Channel";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (START_FOREGROUND_ACTION.equals(intent.getAction())) {
            startForeground(NOTIFICATION_ID, createNotification());
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                LocationRequest locationRequest = LocationRequest.create();
                locationRequest.setInterval(LOCATION_UPDATE_INTERVAL);
                locationRequest.setFastestInterval(LOCATION_UPDATE_INTERVAL);
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            }

        } else if (STOP_FOREGROUND_ACTION.equals(intent.getAction())) {
            stopForeground(true);
            stopSelfResult(startId);
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        return START_STICKY;
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                Intent intent = new Intent("location_update");
                intent.putExtra(LATITUDE, location.getLatitude());
                intent.putExtra(LONGITUDE, location.getLongitude());
                intent.putExtra(ACCURACY, location.getAccuracy());
                LocalBroadcastManager.getInstance(MeasurementService.this).sendBroadcast(intent);
            }
        }
    };

    private Notification createNotification() {
        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.monitoring))
                .setSmallIcon(R.drawable.ramp_logo)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        stopSelf();
        super.onDestroy();
    }
}
