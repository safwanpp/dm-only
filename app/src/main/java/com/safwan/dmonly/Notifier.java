package com.safwan.dmonly;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

// Turns an unread-DM count into one Android notification. Fed by the page
// title while the app is alive and by UnreadWorker while it is not.
final class Notifier {
    private static final String CHANNEL = "dms";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS = "dmonly";
    private static final String KEY_UNREAD = "unread";

    // True while MainActivity is on screen; no alert needed then.
    static volatile boolean foreground;

    private Notifier() {
    }

    static synchronized void unread(Context context, int count) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int last = prefs.getInt(KEY_UNREAD, 0);
        prefs.edit().putInt(KEY_UNREAD, count).apply();

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (count == 0) {
            nm.cancel(NOTIFICATION_ID);
            return;
        }
        // Only alert on new messages, and only when the user is not already looking.
        if (count <= last || foreground) return;

        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH));
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(count == 1 ? "1 unread message" : count + " unread messages")
                .setContentText("Tap to open your inbox")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setNumber(count)
                .build();
        nm.notify(NOTIFICATION_ID, n);
    }

    static void clear(Context context) {
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }
}
