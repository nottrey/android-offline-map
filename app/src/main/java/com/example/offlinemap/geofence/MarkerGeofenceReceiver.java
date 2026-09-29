package com.example.offlinemap.geofence;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.offlinemap.R;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarkerGeofenceReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "marker_geofence";
    private static final long COOLDOWN_MS = 10 * 60 * 1000;
    private static final Map<String, Long> lastNotified = new HashMap<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) {
            return;
        }

        if (event.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_ENTER) {
            return;
        }

        createChannel(context);

        List<Geofence> triggering = event.getTriggeringGeofences();
        if (triggering == null || triggering.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (Geofence g : triggering) {
            String id = g.getRequestId();
            Long last = lastNotified.get(id);
            if (last != null && (now - last) < COOLDOWN_MS) {
                continue;
            }
            lastNotified.put(id, now);

            String title = "Nearby saved marker";
            String body = "You are within 500 m of a saved location.";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.baseline_location_on_24)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);

            NotificationManagerCompat.from(context)
                    .notify(id.hashCode(), builder.build());
        }
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Marker proximity alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications when you are near a saved marker");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
