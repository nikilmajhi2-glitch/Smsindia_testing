package com.smsindia.app.workers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.smsindia.app.MainActivity;
import com.smsindia.app.R;
import com.smsindia.app.utils.SmsStorageHelper;  // Import helper here

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SmsWorker extends Worker {

    private static final String CHANNEL_ID = "sms_worker_channel";
    private static final String TAG = "SmsWorker";
    private final Context context;
    private final FirebaseFirestore db;
    private final String uid;

    public SmsWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = context.getSharedPreferences("SMSINDIA_USER", Context.MODE_PRIVATE);
        this.uid = prefs.getString("mobile", "");
    }

    @NonNull
    @Override
    public Result doWork() {
        if (uid == null || uid.isEmpty()) {
            return Result.failure(new Data.Builder()
                    .putString("error", "User not logged in")
                    .build());
        }

        int simSlot = getInputData().getInt("simSlot", 0); // SIM slot selection (0 or 1)

        setForegroundAsync(createForegroundInfo("Loading SMS tasks..."));

        List<Map<String, Object>> tasks;
        try {
            tasks = loadGlobalTasksSync();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load SMS tasks from Firestore", e);
            return Result.failure(new Data.Builder()
                    .putString("error", "Firestore error: " + e.getMessage())
                    .build());
        }

        if (tasks.isEmpty()) {
            setProgressAsync(new Data.Builder().putInt("sent", 0).putInt("total", 0).build());
            setForegroundAsync(createForegroundInfo("No SMS tasks to send."));
            return Result.success();
        }

        SmsManager sms = getSmsManagerForSimSlot(simSlot);
        int sent = 0;

        try {
            for (Map<String, Object> task : tasks) {
                if (isStopped()) break;

                String phone = (String) task.get("phone");
                String msg = (String) task.get("message");
                String docId = (String) task.get("id");

                if (phone == null || msg == null || docId == null) {
                    Log.e(TAG, "Incomplete SMS task data: phone=" + phone + ", message=" + msg + ", id=" + docId);
                    continue;
                }

                // Clean and normalize phone number (+91 prefix if missing)
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

                    sms.sendTextMessage(cleanPhone, null, msg, null, pi);
                    sent++;

                    // Insert sent SMS into device SMS storage
                    SmsStorageHelper.insertSentSms(context, cleanPhone, msg);

                    setProgressAsync(new Data.Builder()
                            .putInt("sent", sent)
                            .putInt("total", tasks.size())
                            .build());

                    setForegroundAsync(createForegroundInfo("Sent " + sent + "/" + tasks.size()));

                    Thread.sleep(1200); // Pause between messages to avoid spamming
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send SMS to " + cleanPhone, e);
                    // Delete failed task to avoid retry loops
                    db.collection("sms_tasks").document(docId).delete();
                }
            }
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "SmsWorker crashed unexpectedly", e);
            return Result.failure(new Data.Builder()
                    .putString("error", "Send crash: " + e.getMessage())
                    .build());
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
        return SmsManager.getDefault(); // Fallback to default SmsManager
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
            lock.wait(10000); // Wait up to 10 seconds
        }

        if (error[0] != null) {
            throw error[0];
        }

        return tasks;
    }

    private ForegroundInfo createForegroundInfo(String content) {
        createChannel();
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("SMSIndia")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();

        return new ForegroundInfo(1, notification);
    }

    private void createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SMS Worker Channel",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}