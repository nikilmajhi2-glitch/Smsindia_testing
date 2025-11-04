package com.smsindia.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.smsindia.app.services.SmsForegroundService;

public class SmsServiceHelper {

    // Start foreground SMS sending service, with optional SIM slot
    public static void startService(Context context, int simSlot) {
        Intent intent = new Intent(context, SmsForegroundService.class);
        intent.putExtra("simSlot", simSlot);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    // Overload for default SIM slot 0
    public static void startService(Context context) {
        startService(context, 0);
    }

    // Stop the foreground SMS sending service
    public static void stopService(Context context) {
        Intent intent = new Intent(context, SmsForegroundService.class);
        context.stopService(intent);
    }
}