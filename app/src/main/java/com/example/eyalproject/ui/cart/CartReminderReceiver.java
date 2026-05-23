package com.example.eyalproject.ui.cart;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import com.example.eyalproject.MainActivity;
import com.example.eyalproject.R;

/**
 * A BroadcastReceiver responsible for handling system-level broadcasts and triggering
 * local notifications. It primarily handles asynchronous alerts such as purchase
 * confirmations and new service request notifications.
 */
public class CartReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "PURCHASE_CHANNEL";
    private static final int NOTIFICATION_ID = 2;

    /**
     * Receives the broadcast intent, extracts the embedded action, title, and message data,
     * and triggers the corresponding local notification.
     *
     * @param context The Context in which the receiver is running.
     * @param intent  The Intent being received containing action and string extras.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // Extract the action string to determine the specific type of event triggered
        String action = intent.getAction();
        // Retrieve the custom message payload sent along with the broadcast
        String message = intent.getStringExtra("message");
        String title = "Notification";
        // Provide a safe fallback in case the broadcast was sent without a specific message
        if (message == null) {
            message = "A notification event occurred.";
        }
        // Determine the notification title dynamically based on the received action
        if ("PURCHASE_CONFIRMATION".equals(action)) {
            title = "Purchase Confirmation";
        } else if ("NEW_SERVICE_REQUEST".equals(action)) {
            // Attempt to get a custom title from the intent, fallback to a default if missing
            title = intent.getStringExtra("title");
            if (title == null) {
                title = "New Service Request";
            }
        }
        // Proceed to build and display the notification to the user
        showNotification(context, title, message);
    }

    /**
     * Constructs and displays a system notification using the NotificationCompat API.
     * Sets up a PendingIntent to launch the MainActivity when the notification is tapped.
     *
     * @param context The Context used to access the NotificationManager.
     * @param title   The title text for the notification.
     * @param message The detailed body text for the notification.
     */
    private void showNotification(Context context, String title, String message) {
        // Ensure the notification channel exists (mandatory for Android 8.0+)
        createNotificationChannel(context);
        // Prepare the intent that will fire when the user taps the notification
        Intent intent = new Intent(context, MainActivity.class);
        // Clear the backstack so opening from the notification provides a fresh instance
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // Wrap the intent securely in a PendingIntent so the OS can execute it on our behalf
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        // Construct the visual layout and behavior of the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_store) // Set the tiny icon seen in the status bar
                .setContentTitle(title)
                .setContentText(message)
                // Use BigTextStyle so long messages expand instead of getting cut off
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Ensure it pops up prominently
                .setContentIntent(pendingIntent) // Attach the click action
                .setAutoCancel(true); // Automatically dismiss the notification once tapped

        // Dispatch the fully configured notification to the system
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Creates the necessary NotificationChannel for devices running Android 8.0 (Oreo) and above.
     * This is required for notifications to be delivered successfully on newer Android versions.
     *
     * @param context The Context used to access system services.
     */
    private void createNotificationChannel(Context context) {
        // Notification channels are only available (and required) on API level 26 and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "GameZone Notifications";
            String description = "Notifications for gamezone confirmations";
            // Set high importance so notifications make a sound and appear as heads-up alerts
            int importance = NotificationManager.IMPORTANCE_HIGH;
            // Initialize the channel with the defined ID, name, and importance tier
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the configured channel with the Android system
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}