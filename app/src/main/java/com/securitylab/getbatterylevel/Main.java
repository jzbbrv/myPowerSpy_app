package com.securitylab.getbatterylevel;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;

import static com.securitylab.getbatterylevel.Constants.MY_PERMISSIONS_REQUEST_LOCATION;
import static com.securitylab.getbatterylevel.Constants.TAG;

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class Main extends Activity implements CompoundButton.OnCheckedChangeListener {
    private static final String PREF_EXECUTION_STATUS = "ExecutionStatus";
    private PowerManager.WakeLock wakeLock;
    private PowerManager powerManager;
    private LocationManager locationManager;
    AlertDialog.Builder alertBuilder;

    // time to wait before stopping the background service upon stop
    private static final int PRE_STOP_WAIT = 200;

    // UI controls
    private TextView liveView;
    private CheckBox cbGPS;
    private TextView explainGPSMode;
    private CheckBox cbBattery;
    private TextView explainBatteryMode;
    private EditText m_commentTxt;
    private Switch m_startStopSwitch;
    private CheckBox cbOnePhoneSetup;
    private TextView explainOnePhoneSetupTextView;
    private CheckBox cbTwoPhoneSetup;
    private TextView explainTwoPhoneSetupTextView;

    // Receives messages from the background service
    protected static IncomingHandler m_inhandler;

    public Main() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::MyWakelockTag");

        setContentView(R.layout.activity_main);

        cbOnePhoneSetup = findViewById(R.id.cbOnePhoneSetup);
        cbOnePhoneSetup.setChecked(false);
        explainOnePhoneSetupTextView = findViewById(R.id.explainOnePhoneSetupTextView);
        cbOnePhoneSetupListener();

        cbTwoPhoneSetup = findViewById(R.id.cbTwoPhoneSetup);
        cbTwoPhoneSetup.setChecked(false);
        explainTwoPhoneSetupTextView = findViewById(R.id.explainTwoPhoneSetupTextView);
        cbTwoPhoneSetupListener();

        cbGPS = findViewById(R.id.gpsCheckBox);
        cbGPS.setChecked(false);
        cbGPS.setEnabled(false);
        explainGPSMode = findViewById(R.id.explainGPSmode);
        explainGPSMode.setEnabled(false);
        cbGPS_listen();

        cbBattery = findViewById(R.id.signalCheckBox);
        cbBattery.setChecked(false);
        cbBattery.setEnabled(false);
        explainBatteryMode = findViewById(R.id.explainBatteryMode);
        explainBatteryMode.setEnabled(false);
        cbBattery_listen();

        m_commentTxt = findViewById(R.id.comment);
        m_startStopSwitch = findViewById(R.id.start_stop_switch);
        liveView = findViewById(R.id.liveView);
        alertBuilder = new AlertDialog.Builder(this);

        m_inhandler = new IncomingHandler(this);
        m_startStopSwitch.setOnCheckedChangeListener(this);

        checkAppPermissions();

        Log.d(Constants.TAG, "App initialized.");
    }

    protected void checkAppPermissions() {
        Log.d(TAG, "in checkPermissions()");
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermission();
        }
        if (!powerManager.isIgnoringBatteryOptimizations(App.getAppContext().getPackageName())) {
            addToExemptionList();
        }
    }

    private void cbGPS_listen() {
        cbGPS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cbGPS.isChecked()) {
                    cbBattery.setEnabled(false);
                    explainBatteryMode.setEnabled(false);
                } else {
                    cbBattery.setEnabled(true);
                    explainBatteryMode.setEnabled(true);
                }
            }
        });
    }

    private void cbBattery_listen() {
        cbBattery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cbBattery.isChecked()) {
                    cbGPS.setEnabled(false);
                    explainGPSMode.setEnabled(false);
                } else {
                    cbGPS.setEnabled(true);
                    explainGPSMode.setEnabled(true);
                }
            }
        });
    }

    private void cbOnePhoneSetupListener() {
        cbOnePhoneSetup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cbOnePhoneSetup.isChecked()) {
                    cbTwoPhoneSetup.setEnabled(false);
                    explainTwoPhoneSetupTextView.setEnabled(false);
                    cbGPS.setEnabled(false);
                    explainGPSMode.setEnabled(false);
                    cbBattery.setEnabled(false);
                    explainBatteryMode.setEnabled(false);
                } else {
                    cbTwoPhoneSetup.setEnabled(true);
                    explainTwoPhoneSetupTextView.setEnabled(true);
                }
            }
        });
    }

    private void cbTwoPhoneSetupListener() {
        cbTwoPhoneSetup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cbTwoPhoneSetup.isChecked()) {
                    cbOnePhoneSetup.setEnabled(false);
                    explainOnePhoneSetupTextView.setEnabled(false);
                    cbGPS.setEnabled(true);
                    explainGPSMode.setEnabled(true);
                    cbBattery.setEnabled(true);
                    explainBatteryMode.setEnabled(true);
                } else {
                    cbOnePhoneSetup.setEnabled(true);
                    explainOnePhoneSetupTextView.setEnabled(true);
                    cbGPS.setEnabled(false);
                    cbGPS.setChecked(false);
                    explainGPSMode.setEnabled(false);
                    cbBattery.setEnabled(false);
                    cbBattery.setChecked(false);
                    explainBatteryMode.setEnabled(false);

                }
            }
        });
    }

    @Override
    public void onCheckedChanged(CompoundButton btnView, boolean isChecked) {
        Log.d(Constants.TAG, String.format("Changed recording state to %s", isChecked ? "on" : "off"));
        if (isChecked) {
            if (cbBattery.isChecked() || cbGPS.isChecked() || cbOnePhoneSetup.isChecked()) {
                start();
            } else {
                m_startStopSwitch.setChecked(false);
                Toast.makeText(App.getAppContext(), "You must select a mode.", Toast.LENGTH_LONG).show();
            }
        } else {
            stop();
        }
    }

    private void start() {
        //if GPS is required, check whether the phone's location services are enabled
        if (!powerManager.isIgnoringBatteryOptimizations(App.getAppContext().getPackageName()) | ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            m_startStopSwitch.setChecked(false);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.permissionsMissing);
            builder.setMessage(R.string.permissionsMissingMessage);
            builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    checkAppPermissions();
                }
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();
        } else {
            if ((cbGPS.isChecked() || cbOnePhoneSetup.isChecked()) && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                m_startStopSwitch.setChecked(false);
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.locationAlertTitle);
                builder.setMessage(R.string.locationAlertMessage);
                builder.setPositiveButton(R.string.opensettings, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Intent locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(locationIntent);
                    }
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
            } else {
                enableDisableControls(false);
                wakeLock.acquire(Constants.WAKE_LOCK_TIMEOUT);
                Log.d(Constants.TAG, "wakeLock locked.");
                startService();
            }
        }
    }

    public void startService() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Send command to background service with user's mode selection + comment
        Intent startRecIntent = new Intent(App.getAppContext(), BackgroundRecorder.class);
        startRecIntent.putExtra(Constants.EXTRA_ONEPHONE, cbOnePhoneSetup.isChecked());
        startRecIntent.putExtra(Constants.EXTRA_GPS, cbGPS.isChecked());
        startRecIntent.putExtra(Constants.EXTRA_BATTERY, cbBattery.isChecked());
        startRecIntent.putExtra(Constants.EXTRA_COMMENT, m_commentTxt.getText().toString());
        App.getAppContext().startService(startRecIntent);
    }

    private void stop() {
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enableDisableControls(true);
        cbOnePhoneSetup.setChecked(false);
        cbTwoPhoneSetup.setChecked(false);
        cbGPS.setChecked(false);
        cbBattery.setChecked(false);
        wakeLock.release();

        Log.d(Constants.TAG, "wakeLock released.");
        Intent stopRecIntent = new Intent(App.getAppContext(), BackgroundRecorder.class);
        App.getAppContext().stopService(stopRecIntent);
        try {
            Thread.sleep(PRE_STOP_WAIT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void enableDisableControls(boolean enable) {
        if (enable) {
            cbOnePhoneSetup.setEnabled(enable);
            explainOnePhoneSetupTextView.setEnabled(enable);
            cbTwoPhoneSetup.setEnabled(enable);
            explainTwoPhoneSetupTextView.setEnabled(enable);
            m_commentTxt.setEnabled(enable);
        } else {
            cbOnePhoneSetup.setEnabled(enable);
            explainOnePhoneSetupTextView.setEnabled(enable);
            cbTwoPhoneSetup.setEnabled(enable);
            explainTwoPhoneSetupTextView.setEnabled(enable);
            cbGPS.setEnabled(enable);
            explainGPSMode.setEnabled(enable);
            cbBattery.setEnabled(enable);
            explainBatteryMode.setEnabled(enable);
            m_commentTxt.setEnabled(enable);
        }
    }

    public void checkLocationPermission() {
        Log.d(TAG, "in checkLocationPermission()");
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            Log.d(TAG, "shouldShowRequestPermissionRationale: true");
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.locationRationale);
            builder.setMessage(R.string.locationRationaleMessage);
            builder.setPositiveButton(R.string.opensettings, null);
            builder.show();
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(Constants.TAG, "requesting background location permission.");
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        Log.d(TAG, "in onRequestPermissionResult()");
        if (requestCode == MY_PERMISSIONS_REQUEST_LOCATION) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(App.getAppContext(), R.string.permission_granted, Toast.LENGTH_LONG).show();
            } else {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "showing location requirement prompt");
                    alertBuilder
                            .setTitle("Location Access Required")
                            .setMessage(R.string.permission_explanation)
                            .setPositiveButton("Open settings", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Intent exemptionListIntent = new Intent();
                                    exemptionListIntent.setAction(Settings.ACTION_APPLICATION_SETTINGS);
                                    startActivity(exemptionListIntent);
                                }
                            })
                            .setNegativeButton(R.string.closeapp, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    finish();
                                }
                            })
                            .setCancelable(false)
                            .create()
                            .show();
                }
            }
        }
    }

    public void addToExemptionList() {
        Log.d(TAG, "in addToExemptionList()");
        if (!powerManager.isIgnoringBatteryOptimizations(App.getAppContext().getPackageName())) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.exemptionAlertTitle);
            builder.setMessage(R.string.exemptionAlertMessage);
            builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                @SuppressLint("BatteryLife")
                public void onClick(DialogInterface dialog, int id) {
                    Intent exemptionListIntent = new Intent();
                    exemptionListIntent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    exemptionListIntent.setData(Uri.parse("package:" + App.getAppContext().getPackageName()));
                    Log.d(TAG, "package:" + App.getAppContext().getPackageName());
                    startActivity(exemptionListIntent);
                }
            });
            builder.setNegativeButton(R.string.closeapp, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    finish();
                }
            });
            builder.setCancelable(false);
            builder.show();
        }
    }

    private boolean getExecutionStatus() {
        final Switch start_stop_switch = (Switch) findViewById(R.id.start_stop_switch);
        return start_stop_switch.isChecked();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PREF_EXECUTION_STATUS, getExecutionStatus());
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        final boolean isRunning = savedInstanceState.getBoolean(PREF_EXECUTION_STATUS, false);

        // set switch state to reflect service execution status
        Switch start_stop_switch = (Switch) findViewById(R.id.start_stop_switch);
        start_stop_switch.setChecked(isRunning);
    }

    public void handleMessage(Message msg) {
        Log.d(Constants.TAG, msg.obj.toString());
        liveView.setText(msg.obj.toString());
    }

    static class IncomingHandler extends Handler {
        private final WeakReference<Main> mainActivity;

        IncomingHandler(Main activity) {
            mainActivity = new WeakReference<Main>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            Main activity = mainActivity.get();
            if (activity != null) {
                activity.handleMessage(msg);
            }
        }
    }

    public void onDestroy() {
        super.onDestroy();
        stop();
    }
}