package com.smsindia.app.ui;

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
import androidx.fragment.app.Fragment;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.smsindia.app.R;

public class TaskFragment extends Fragment {
    Button btnStartSim1, btnStartSim2;
    ProgressBar progressSim1, progressSim2;
    TextView limitSim1, limitSim2;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_task, container, false);

        btnStartSim1 = v.findViewById(R.id.btn_start_sim1);
        btnStartSim2 = v.findViewById(R.id.btn_start_sim2);
        progressSim1 = v.findViewById(R.id.progress_sim1);
        progressSim2 = v.findViewById(R.id.progress_sim2);
        limitSim1 = v.findViewById(R.id.limit_sim1);
        limitSim2 = v.findViewById(R.id.limit_sim2);

        btnStartSim1.setOnClickListener(view -> startSmsWorker(0));
        btnStartSim2.setOnClickListener(view -> startSmsWorker(1));

        progressSim1.setProgress(0);
        progressSim2.setProgress(0);

        limitSim1.setText("Limit / Send : 100 / 0");
        limitSim2.setText("Limit / Send : 100 / 0");

        return v;
    }

    private void startSmsWorker(int simSlot) {
        Data inputData = new Data.Builder().putInt("simSlot", simSlot).build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(com.smsindia.app.workers.SmsWorker.class)
                .setInputData(inputData).build();
        WorkManager.getInstance(requireContext()).enqueue(work);

        Toast.makeText(getContext(), "Started task for SIM " + (simSlot + 1), Toast.LENGTH_SHORT).show();
    }
}