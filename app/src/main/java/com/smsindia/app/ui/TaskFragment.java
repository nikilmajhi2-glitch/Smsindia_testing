package com.smsindia.app.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;

public class TaskFragment extends Fragment {

    private static final int SMS_PERMISSION_CODE = 1001;

    private Button startBtn, stopBtn, viewLogsBtn;
    private TextView statusMessage, failHint;
    private ProgressBar sendingProgress;
    private CardView statusCard;

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
            if (!hasAllSmsPermissions()) {
                String message = "Please allow ALL SMS & notification permissions in Settings.";
                statusMessage.setText(message);
                statusCard.setCardBackgroundColor(Color.parseColor("#FFCDD2"));
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                checkAndRequestSmsPermissions();
                return;
            }
            startSmsSending();
        });

        stopBtn.setOnClickListener(view -> stopSmsSending());

        viewLogsBtn.setOnClickListener(v1 -> startActivity(new Intent(requireContext(), DeliveryLogActivity.class)));

        stopBtn.setEnabled(false);
        showReadyUI();

        return v;
    }

    // Permission check across all needed permissions
    private boolean hasAllSmsPermissions() {
        boolean hasSend = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean hasRead = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean hasReceive = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean hasNotif = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotif = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return hasSend && hasRead && hasReceive && hasNotif;
    }

    // One dialog for all permissions at once
    private void checkAndRequestSmsPermissions() {
        List<String> perms = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.SEND_SMS);
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.RECEIVE_SMS);
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.READ_SMS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.POST_NOTIFICATIONS);

        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(),
                perms.toArray(new String[0]), SMS_PERMISSION_CODE);
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

    private void startSmsSending() {
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
}