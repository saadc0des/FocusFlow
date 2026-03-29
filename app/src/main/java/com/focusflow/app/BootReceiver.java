package com.focusflow.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Show a notification reminding the user FocusFlow needs accessibility on
            String channelId = "focusflow_boot";
            NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel channel = new NotificationChannel(
                channelId, "FocusFlow", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);

            Intent openApp = new Intent(context, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(context, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("FocusFlow")
                .setContentText("Tap to make sure protection is active after reboot.")
                .setContentIntent(pi)
                .setAutoCancel(true);

            nm.notify(1, builder.build());
        }
    }
}
