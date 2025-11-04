package com.smsindia.app.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.smsindia.app.R;
import com.smsindia.app.services.SmsForegroundService;

public class TaskFragment extends Fragment {

    private static final int SMS_PERMISSION_CODE = 1001;
    private static final int REQUEST_DEFAULT_SMS_APP = 1002;

    private Button startBtn, stopBtn, viewLogsBtn;
    private TextView statusMessage, failHint;
    private ProgressBar sendingProgress;
    private CardView statusCard;

    private BroadcastReceiver progressReceiver;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_task, container, false);

        statusCard = v.findViewById(R.id.status_card);
        statusMessage = v.findViewById(R.id.status_message);
        failHint = v.findViewById(R.id.fail_hint);
        sendingProgress = v.findViewById(R.id.sending_progress);

        startBtn = v.findViewById(R.id.btn_start_sending);
        stopBtn = v.findViewById(R.id.btn_stop_sending);
        viewLogsBtn = v.findViewById(R.id.btn_view_logs);

        checkAndRequestSmsPermissions();

        startBtn.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!isDefaultSmsApp()) {
                    promptSetDefaultSmsApp();
                    return;
                }
            }
            startSmsSending();
        });

        stopBtn.setOnClickListener(view -> stopSmsSending());

        viewLogsBtn.setOnClickListener(v1 -> startActivity(new Intent(requireContext(), DeliveryLogActivity.class)));

        stopBtn.setEnabled(false);
        showReadyUI();

        setupProgressReceiver();

        return v;
    }

    private void setupProgressReceiver() {
        progressReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int sent = intent.getIntExtra("sent", -1);
                int total = intent.getIntExtra("total", -1);
                String status = intent.getStringExtra("status");

                if (sent >= 0 && total >= 0) {
                    statusMessage.setText("Sending SMS: " + sent + "/" + total);
                    sendingProgress.setVisibility(View.VISIBLE);
                } else if (status != null) {
                    statusMessage.setText(status);
                    sendingProgress.setVisibility(View.GONE);
                }
                startBtn.setEnabled(false);
                stopBtn.setEnabled(true);
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        requireContext().registerReceiver(progressReceiver, new IntentFilter("SMS_PROGRESS_UPDATE"));
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(progressReceiver);
    }

    private boolean isDefaultSmsApp() {
        return Telephony.Sms.getDefaultSmsPackage(requireContext()).equals(requireContext().getPackageName());
    }

    private void promptSetDefaultSmsApp() {
        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, requireContext().getPackageName());
        startActivityForResult(intent, REQUEST_DEFAULT_SMS_APP);
    }

    private void startSmsSending() {
        if (!hasSmsPermissions()) {
            statusMessage.setText("SMS permission missing. Please grant and retry.");
            statusCard.setCardBackgroundColor(Color.parseColor("#FFCDD2"));
            Toast.makeText(getContext(), "Please grant SMS permission", Toast.LENGTH_LONG).show();
            return;
        }

        Intent serviceIntent = new Intent(requireContext(), SmsForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent);
        } else {
            requireContext().startService(serviceIntent);
        }

        statusMessage.setText("Started sending SMS tasks...");
        statusCard.setCardBackgroundColor(Color.parseColor("#FFFDE7"));
        sendingProgress.setVisibility(View.VISIBLE);
        failHint.setVisibility(View.GONE);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
    }

    private void stopSmsSending() {
        Intent serviceIntent = new Intent(requireContext(), SmsForegroundService.class);
        requireContext().stopService(serviceIntent);

        statusMessage.setText("Sending stopped.");
        statusCard.setCardBackgroundColor(Color.parseColor("#FFECB3"));
        sendingProgress.setVisibility(View.GONE);
        failHint.setVisibility(View.GONE);
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
    }

    private void showReadyUI() {
        statusMessage.setText("Ready to send SMS");
        statusCard.setCardBackgroundColor(Color.parseColor("#FFFFFF"));
        sendingProgress.setVisibility(View.GONE);
        failHint.setVisibility(View.GONE);
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void checkAndRequestSmsPermissions() {
        if (!hasSmsPermissions()) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.READ_SMS,
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    SMS_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            boolean granted = true;
            for (int res : grantResults)
                if (res != PackageManager.PERMISSION_GRANTED) granted = false;
            Toast.makeText(getContext(),
                    granted ? "Permissions OK" : "Allow all permissions",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEFAULT_SMS_APP) {
            if (isDefaultSmsApp()) {
                Toast.makeText(getContext(), "App set as default SMS app", Toast.LENGTH_SHORT).show();
                startSmsSending();
            } else {
                Toast.makeText(getContext(), "App not set as default SMS app", Toast.LENGTH_SHORT).show();
            }
        }
    }
}