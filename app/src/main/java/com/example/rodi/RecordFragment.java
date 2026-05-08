package com.example.rodi;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.IOException;

public class RecordFragment extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    // UI
    private Button btnRecord;
    private Button btnPreview;
    private TextView tvStatus;

    // Recording
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private String outputFilePath;
    private boolean isRecording = false;
    private boolean hasRecording = false;

    // Permission launcher
    private final ActivityResultLauncher<String> requestMicrophonePermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    beginRecording();
                } else {
                    Toast.makeText(getContext(), "Microphone permission is required to record.", Toast.LENGTH_SHORT).show();
                }
            });

    public RecordFragment() {}

    public static RecordFragment newInstance(String param1, String param2) {
        RecordFragment fragment = new RecordFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        outputFilePath = requireContext().getExternalCacheDir().getAbsolutePath() + "/rodi_recording.m4a";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_record, container, false);

        tvStatus   = view.findViewById(R.id.tv_record_status);
        btnRecord  = view.findViewById(R.id.btn_record);
        btnPreview = view.findViewById(R.id.btn_preview);

        btnPreview.setEnabled(false);

        btnRecord.setOnClickListener(v -> toggleRecording());
        btnPreview.setOnClickListener(v -> togglePlayback());

        return view;
    }

    // ── Record button ──────────────────────────────────────────────────────────

    private void toggleRecording() {
        if (isRecording) {
            saveAndStopRecording();
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                beginRecording();
            } else {
                requestMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO);
            }
        }
    }

    private void beginRecording() {
        stopPlayback(); // stop any active playback first

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(outputFilePath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            hasRecording = false;

            btnRecord.setText("Stop");
            btnPreview.setEnabled(false);
            tvStatus.setText("Recording…");
        } catch (IOException e) {
            cleanupRecorder();
            Toast.makeText(getContext(), "Failed to start recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAndStopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (RuntimeException e) {
                // Recording was too short; discard the file
                new File(outputFilePath).delete();
                hasRecording = false;
                tvStatus.setText("Recording too short – try again.");
            } finally {
                cleanupRecorder();
            }

            if (new File(outputFilePath).exists()) {
                hasRecording = true;
                tvStatus.setText("Recording saved. Tap Preview to listen.");
                btnPreview.setEnabled(true);
            }
        }

        isRecording = false;
        btnRecord.setText("Record");
    }

    // ── Preview button ─────────────────────────────────────────────────────────

    private void togglePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            stopPlayback();
            return;
        }

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(outputFilePath);
            mediaPlayer.prepare();
            mediaPlayer.start();

            btnPreview.setText("Stop Preview");
            tvStatus.setText("Playing back recording…");

            mediaPlayer.setOnCompletionListener(mp -> {
                stopPlayback();
                tvStatus.setText("Playback finished.");
            });
        } catch (IOException e) {
            cleanupPlayer();
            Toast.makeText(getContext(), "Failed to play recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            cleanupPlayer();
        }
        btnPreview.setText("Preview");
    }

    // ── Lifecycle cleanup ──────────────────────────────────────────────────────

    @Override
    public void onStop() {
        super.onStop();
        if (isRecording) saveAndStopRecording();
        stopPlayback();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cleanupRecorder();
        cleanupPlayer();
    }

    private void cleanupRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void cleanupPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (btnPreview != null) btnPreview.setText("Preview");
    }
}