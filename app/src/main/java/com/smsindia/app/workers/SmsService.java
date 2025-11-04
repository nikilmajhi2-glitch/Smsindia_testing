package com.smsindia.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.smsindia.app.MainActivity;
import com.smsindia.app.R;
import com.smsindia.app.utils.SmsStorageHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SmsForegroundService extends Service {

    private static final String TAG = "SmsForegroundService";
    private static final String CHANNEL_ID = "sms_foreground_service_channel";
    private static final int NOTIFICATION_ID = 1;

    private volatile boolean isRunning = false;
    private Thread workerThread;

    private FirebaseFirestore db;
    private Context context;
    private String uid;

    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();
        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = context.getSharedPreferences("SMSINDIA_USER", Context.MODE_PRIVATE);
        uid = prefs.getString("mobile", "");

        createNotificationChannel();

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("SMSIndia")
                .setContentText("Sending SMS messages...")
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        isRunning = true;

        workerThread = new Thread(this::runMessageLoop);
        workerThread.start();
    }

    private void runMessageLoop() {
        int simSlot = 0; // You can make this configurable or fetch from Intent extras if needed
        SmsManager smsManager = getSmsManagerForSimSlot(simSlot);

        while (isRunning) {
            try {
                if (uid == null || uid.isEmpty()) {
                    Log.e(TAG, "User not logged in, skipping SMS sending");
                    Thread.sleep(10000);
                    continue;
                }

                List<Map<String, Object>> tasks = loadGlobalTasksSync();
                if (tasks.isEmpty()) {
                    Log.d(TAG, "No SMS tasks found");
                    Thread.sleep(10000);
                    continue;
                }

                int sentCount = 0;
                for (Map<String, Object> task : tasks) {
                    if (!isRunning) break;

                    String phone = (String) task.get("phone");
                    String msg = (String) task.get("message");
                    String docId = (String) task.get("id");

                    if (phone == null || msg == null || docId == null) {
                        Log.e(TAG, "Incomplete SMS task data: phone=" + phone + ", message=" + msg + ", id=" + docId);
                        continue;
                    }

                    String cleanPhone = phone.replaceAll("[^0-9+]", "");
                    if (!cleanPhone.startsWith("+")) {
                        cleanPhone = "+91" + cleanPhone;
                    }

                    Log.d(TAG, "Sending SMS to: " + cleanPhone + " | Message: " + msg);

                    try {
                        Intent deliveredIntent = new Intent("com.smsindia.SMS_DELIVERED");
                        deliveredIntent.putExtra("userId", uid);
                        deliveredIntent.putExtra("docId", docId);
                        deliveredIntent.putExtra("phone", cleanPhone);

                        PendingIntent pi = PendingIntent.getBroadcast(
                                context,
                                docId.hashCode(),
                                deliveredIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        );

                        smsManager.sendTextMessage(cleanPhone, null, msg, null, pi);
                        sentCount++;

                        SmsStorageHelper.insertSentSms(context, cleanPhone, msg);

                        // Optionally update notification with progress
                        updateNotification("Sent " + sentCount + "/" + tasks.size());

                        Thread.sleep(1200);

                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send SMS to " + cleanPhone, e);
                        db.collection("sms_tasks").document(docId).delete();
                    }
                }

                Thread.sleep(10000); // Wait 10 seconds before checking new tasks

            } catch (Exception e) {
                Log.e(TAG, "Exception in SmsForegroundService loop", e);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ie) {
                    Log.e(TAG, "Worker thread interrupted", ie);
                }
            }
        }
    }

    private void updateNotification(String contentText) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("SMSIndia")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private SmsManager getSmsManagerForSimSlot(int simSlot) {
        try {
            SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (subscriptionManager != null) {
                List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
                if (subscriptionInfoList != null && subscriptionInfoList.size() > simSlot) {
                    int subscriptionId = subscriptionInfoList.get(simSlot).getSubscriptionId();
                    return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error obtaining SmsManager for SIM slot " + simSlot, e);
        }
        return SmsManager.getDefault();
    }

    private List<Map<String, Object>> loadGlobalTasksSync() throws Exception {
        final List<Map<String, Object>> tasks = new ArrayList<>();
        final Object lock = new Object();
        final Exception[] error = {null};

        db.collection("sms_tasks")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            data.put("id", doc.getId());
                            tasks.add(data);
                        }
                    }
                    synchronized (lock) {
                        lock.notify();
                    }
                })
                .addOnFailureListener(e -> {
                    error[0] = e;
                    synchronized (lock) {
                        lock.notify();
                    }
                });

        synchronized (lock) {
            lock.wait(10000);
        }

        if (error[0] != null) {
            throw error[0];
        }

        return tasks;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "SMS Foreground Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Service is sticky so it will restart if killed
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (workerThread != null && workerThread.isAlive()) {
            workerThread.interrupt();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // No binding provided
        return null;
    }
}