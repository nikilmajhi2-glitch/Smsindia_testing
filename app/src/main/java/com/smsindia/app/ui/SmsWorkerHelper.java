package com.smsindia.app.ui;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class SmsWorkerHelper {

    public static final String UNIQUE_WORK_NAME = "sms_worker_unique";
    public static final String WORK_TAG = "sms_worker_tag";

    // Enqueue work with optional simSlot (default to 0)
    public static void enqueueWork(@NonNull WorkManager workManager, int simSlot) {
        Data inputData = new Data.Builder()
                .putInt("simSlot", simSlot)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(com.smsindia.app.workers.SmsWorker.class)
                .setInputData(inputData)
                .addTag(WORK_TAG)
                .build();

        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    // Overload for default SIM slot 0
    public static void enqueueWork(@NonNull WorkManager workManager) {
        enqueueWork(workManager, 0);
    }

    // Cancel ongoing work
    public static void cancelWork(@NonNull WorkManager workManager) {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME);
    }
}