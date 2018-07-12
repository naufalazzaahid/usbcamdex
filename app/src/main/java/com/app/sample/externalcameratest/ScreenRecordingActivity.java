package com.app.sample.externalcameratest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.app.sample.externalcameratest.service.ScreenRecorderService;
import com.serenegiant.dialog.MessageDialogFragment;
import com.serenegiant.utils.BuildCheck;
import com.serenegiant.utils.PermissionCheck;


import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.ref.WeakReference;

public class ScreenRecordingActivity extends Activity
        implements MessageDialogFragment.MessageDialogListener {

    private static final boolean DEBUG = true;
    private static final String TAG = "MainActivity";

    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1;

    private ToggleButton mRecordButton;
    private ToggleButton mPauseButton;
    private MyBroadcastReceiver mReceiver;

    private int mScreenDensity;
    private Surface mSurface;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private SurfaceView mSurfaceView;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.v(TAG, "onCreate:");
        setContentView(R.layout.activity_screen_recording);
        mRecordButton = (ToggleButton)findViewById(R.id.record_button);
        mPauseButton = (ToggleButton)findViewById(R.id.pause_button);

        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurface = mSurfaceView.getHolder().getSurface();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        updateRecording(false, false);
        if (mReceiver == null) {
            mReceiver = new MyBroadcastReceiver(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DEBUG) Log.v(TAG, "onResume:");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ScreenRecorderService.ACTION_QUERY_STATUS_RESULT);
        registerReceiver(mReceiver, intentFilter);
        queryRecordingStatus();
    }

    @Override
    protected void onPause() {
        if (DEBUG) Log.v(TAG, "onPause:");
        unregisterReceiver(mReceiver);
        super.onPause();
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (DEBUG) Log.v(TAG, "onActivityResult:resultCode=" + resultCode + ",data=" + data);
        super.onActivityResult(requestCode, resultCode, data);
        if (REQUEST_CODE_SCREEN_CAPTURE == requestCode) {
            if (resultCode != Activity.RESULT_OK) {
                // when no permission
                Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                return;
            }
            startScreenRecorder(resultCode, data);
            //moveTaskToBack(true);
        }
    }

    private final CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
            switch (buttonView.getId()) {
                case R.id.record_button:
                    if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
                        if (isChecked) {
                            final MediaProjectionManager manager
                                    = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                            final Intent permissionIntent = manager.createScreenCaptureIntent();
                            startActivityForResult(permissionIntent, REQUEST_CODE_SCREEN_CAPTURE);
                        } else {
                            final Intent intent = new Intent(ScreenRecordingActivity.this, ScreenRecorderService.class);
                            intent.setAction(ScreenRecorderService.ACTION_STOP);
                            startService(intent);
                        }
                    } else {
                        mRecordButton.setOnCheckedChangeListener(null);
                        try {
                            mRecordButton.setChecked(false);
                        } finally {
                            mRecordButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
                        }
                    }
                    break;
                case R.id.pause_button:
                    if (isChecked) {
                        final Intent intent = new Intent(ScreenRecordingActivity.this, ScreenRecorderService.class);
                        intent.setAction(ScreenRecorderService.ACTION_PAUSE);
                        startService(intent);
                    } else {
                        final Intent intent = new Intent(ScreenRecordingActivity.this, ScreenRecorderService.class);
                        intent.setAction(ScreenRecorderService.ACTION_RESUME);
                        startService(intent);
                    }
                    break;
            }
        }
    };

    private void queryRecordingStatus() {
        if (DEBUG) Log.v(TAG, "queryRecording:");
        final Intent intent = new Intent(this, ScreenRecorderService.class);
        intent.setAction(ScreenRecorderService.ACTION_QUERY_STATUS);
        startService(intent);
    }

    private void startScreenRecorder(final int resultCode, final Intent data) {
        final Intent intent = new Intent(this, ScreenRecorderService.class);
        intent.setAction(ScreenRecorderService.ACTION_START);
        intent.putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, resultCode);
        intent.putExtras(data);
        startService(intent);
    }

    private void updateRecording(final boolean isRecording, final boolean isPausing) {
        if (DEBUG) Log.v(TAG, "updateRecording:isRecording=" + isRecording + ",isPausing=" + isPausing);
        mRecordButton.setOnCheckedChangeListener(null);
        mPauseButton.setOnCheckedChangeListener(null);
        try {
            mRecordButton.setChecked(isRecording);
            mPauseButton.setEnabled(isRecording);
            mPauseButton.setChecked(isPausing);
        } finally {
            mRecordButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
            mPauseButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
        }
    }

    private static final class MyBroadcastReceiver extends BroadcastReceiver {
        private final WeakReference<ScreenRecordingActivity> mWeakParent;
        public MyBroadcastReceiver(final ScreenRecordingActivity parent) {
            mWeakParent = new WeakReference<ScreenRecordingActivity>(parent);
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (DEBUG) Log.v(TAG, "onReceive:" + intent);
            final String action = intent.getAction();
            if (ScreenRecorderService.ACTION_QUERY_STATUS_RESULT.equals(action)) {
                final boolean isRecording = intent.getBooleanExtra(ScreenRecorderService.EXTRA_QUERY_RESULT_RECORDING, false);
                final boolean isPausing = intent.getBooleanExtra(ScreenRecorderService.EXTRA_QUERY_RESULT_PAUSING, false);
                final ScreenRecordingActivity parent = mWeakParent.get();
                if (parent != null) {
                    parent.updateRecording(isRecording, isPausing);
                }
            }
        }
    }

    private void setUpVirtualDisplay() {
        Log.i("coba", "Setting up a VirtualDisplay: " +
                mSurfaceView.getWidth() + "x" + mSurfaceView.getHeight() +
                " (" + mScreenDensity + ")");
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                mSurfaceView.getWidth(), mSurfaceView.getHeight(), mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface, null, null);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
    public void onMessageEvent(MediaProjection event) {
        Log.d("coba", "onMessageEvent: " + mMediaProjection);
        this.mMediaProjection = event;
        Log.d("coba", "onMessageEvent: " + mMediaProjection);

        setUpVirtualDisplay();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

//================================================================================
// methods related to new permission model on Android 6 and later
//================================================================================
    /**
     * Callback listener from MessageDialogFragmentV4
     * @param dialog
     * @param requestCode
     * @param permissions
     * @param result
     */
    @SuppressLint("NewApi")
    @Override
    public void onMessageDialogResult(final MessageDialogFragment dialog, final int requestCode, final String[] permissions, final boolean result) {
        if (result) {
            // request permission(s) when user touched/clicked OK
            if (BuildCheck.isMarshmallow()) {
                requestPermissions(permissions, requestCode);
                return;
            }
        }
        // check permission and call #checkPermissionResult when user canceled or not Android6(and later)
        for (final String permission: permissions) {
            checkPermissionResult(requestCode, permission, PermissionCheck.hasPermission(this, permission));
        }
    }

    /**
     * callback method when app(Fragment) receive the result of permission result from ANdroid system
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);	// 何もしてないけど一応呼んどく
        final int n = Math.min(permissions.length, grantResults.length);
        for (int i = 0; i < n; i++) {
            checkPermissionResult(requestCode, permissions[i], grantResults[i] == PackageManager.PERMISSION_GRANTED);
        }
    }

    /**
     * check the result of permission request
     * if app still has no permission, just show Toast
     * @param requestCode
     * @param permission
     * @param result
     */
    protected void checkPermissionResult(final int requestCode, final String permission, final boolean result) {
        // show Toast when there is no permission
        if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
            onUpdateAudioPermission(result);
            if (!result) {
                Toast.makeText(this, R.string.permission_audio, Toast.LENGTH_SHORT).show();
            }
        }
        if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
            onUpdateExternalStoragePermission(result);
            if (!result) {
                Toast.makeText(this, R.string.permission_ext_storage, Toast.LENGTH_SHORT).show();
            }
        }
        if (Manifest.permission.INTERNET.equals(permission)) {
            onUpdateNetworkPermission(result);
            if (!result) {
                Toast.makeText(this, R.string.permission_network, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * called when user give permission for audio recording or canceled
     * @param hasPermission
     */
    protected void onUpdateAudioPermission(final boolean hasPermission) {
    }

    /**
     * called when user give permission for accessing external storage or canceled
     * @param hasPermission
     */
    protected void onUpdateExternalStoragePermission(final boolean hasPermission) {
    }

    /**
     * called when user give permission for accessing network or canceled
     * this will not be called
     * @param hasPermission
     */
    protected void onUpdateNetworkPermission(final boolean hasPermission) {
    }

    protected static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 0x01;
    protected static final int REQUEST_PERMISSION_AUDIO_RECORDING = 0x02;
    protected static final int REQUEST_PERMISSION_NETWORK = 0x03;

    /**
     * check whether this app has write external storage
     * if this app has no permission, show dialog
     * @return true this app has permission
     */
    protected boolean checkPermissionWriteExternalStorage() {
        if (!PermissionCheck.hasWriteExternalStorage(this)) {
            MessageDialogFragment.showDialog(this, REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE,
                    R.string.permission_title, R.string.permission_ext_storage_request,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE});
            return false;
        }
        return true;
    }

    /**
     * check whether this app has permission of audio recording
     * if this app has no permission, show dialog
     * @return true this app has permission
     */
    protected boolean checkPermissionAudio() {
        if (!PermissionCheck.hasAudio(this)) {
            MessageDialogFragment.showDialog(this, REQUEST_PERMISSION_AUDIO_RECORDING,
                    R.string.permission_title, R.string.permission_audio_recording_request,
                    new String[]{Manifest.permission.RECORD_AUDIO});
            return false;
        }
        return true;
    }

    /**
     * check whether permission of network access
     * if this app has no permission, show dialog
     * @return true this app has permission
     */
    protected boolean checkPermissionNetwork() {
        if (!PermissionCheck.hasNetwork(this)) {
            MessageDialogFragment.showDialog(this, REQUEST_PERMISSION_NETWORK,
                    R.string.permission_title, R.string.permission_network_request,
                    new String[]{Manifest.permission.INTERNET});
            return false;
        }
        return true;
    }
}
