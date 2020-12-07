package com.securitylab.getbatterylevel;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;

import static com.securitylab.getbatterylevel.Constants.MY_PERMISSIONS_REQUEST_LOCATION;

public class Main extends Activity implements CompoundButton.OnCheckedChangeListener {
	private static final String PREF_EXECUTION_STATUS = "ExecutionStatus";
	private PowerManager.WakeLock wakeLock;
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
	
	private ServiceConnection m_serviceConnection;

	public Main() {
	}
	/*
	private boolean isMyServiceRunning(Class<?> serviceClass) {
	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (serviceClass.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	*/

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
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

	    /*
		Log.d(Constants.TAG, "Checking whether service is running..");
	    if (isMyServiceRunning(BackgroundRecorder.class)) {
	    	Log.d(Constants.TAG, "Service is running. Trying to bind...");
	    	bindToService();
	    }
		*/

	    //check whether user allowed app to access the phone's location via GPS
	    //checkLocationPermission();

        m_inhandler = new IncomingHandler(this);
	    m_startStopSwitch.setOnCheckedChangeListener(this);

        Log.d(Constants.TAG, "App initialized.");
    }

    @Override
    protected void onStart() {
		super.onStart();
		checkLocationPermission();
	}

	private void cbGPS_listen() {
		cbGPS.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(cbGPS.isChecked()) {
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
				if(cbBattery.isChecked()) {
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
				if(cbOnePhoneSetup.isChecked()) {
					cbTwoPhoneSetup.setEnabled(false);
					explainTwoPhoneSetupTextView.setEnabled(false);
					cbGPS.setEnabled(false);
					explainGPSMode.setEnabled(false);
					cbBattery.setEnabled(false);
					explainBatteryMode.setEnabled(false);
				} else {
					cbTwoPhoneSetup.setEnabled(true);
					explainTwoPhoneSetupTextView.setEnabled(true);
					cbGPS.setEnabled(true);
					explainGPSMode.setEnabled(true);
					cbBattery.setEnabled(true);
					explainBatteryMode.setEnabled(true);
				}
			}
		});
	}

	private void cbTwoPhoneSetupListener() {
		cbTwoPhoneSetup.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(cbTwoPhoneSetup.isChecked()) {
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
					explainGPSMode.setEnabled(false);
					cbBattery.setEnabled(false);
					explainBatteryMode.setEnabled(false);
				}
			}
		});
	}

	@Override
	public void onCheckedChanged(CompoundButton btnView, boolean isChecked)
	{
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

	// Start recording
	private void start() {
		//disable all controls
		enableDisableControls(false);

		//when app is on exemption list (excluded from battery optimization), partial wake lock ensures that CPU keeps running
		wakeLock.acquire(Constants.WAKE_LOCK_TIMEOUT);
		Log.d(Constants.TAG, "wakeLock locked.");

        //activateDoubleTapListener();

		// Send command to background service with user's mode selection + comment
		Intent startRecIntent = new Intent(App.getAppContext(), BackgroundRecorder.class);
		startRecIntent.putExtra(Constants.EXTRA_ONEPHONE, cbOnePhoneSetup.isChecked());
		startRecIntent.putExtra(Constants.EXTRA_GPS, cbGPS.isChecked());
		startRecIntent.putExtra(Constants.EXTRA_BATTERY, cbBattery.isChecked());
		startRecIntent.putExtra(Constants.EXTRA_COMMENT, m_commentTxt.getText().toString());

		App.getAppContext().startService(startRecIntent);
	}

	private void stop() {
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
		cbGPS.setEnabled(enable);
		explainGPSMode.setEnabled(enable);
		cbBattery.setEnabled(enable);
		explainBatteryMode.setEnabled(enable);
		m_commentTxt.setEnabled(enable);
		cbOnePhoneSetup.setEnabled(enable);
		explainOnePhoneSetupTextView.setEnabled(enable);
		cbTwoPhoneSetup.setEnabled(enable);
		explainTwoPhoneSetupTextView.setEnabled(enable);
	}

	public void checkLocationPermission() {
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED) {
			Log.d(Constants.TAG, "requesting location permission.");
			requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
					MY_PERMISSIONS_REQUEST_LOCATION);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions,
										   int[] grantResults) {
		switch (requestCode) {
			case MY_PERMISSIONS_REQUEST_LOCATION:
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 &&
						grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					Toast.makeText(App.getAppContext(), R.string.permission_granted, Toast.LENGTH_LONG).show();
				}  else {
					alertBuilder
							.setTitle("Location Access Required")
							.setMessage(R.string.permission_explanation)
							.setPositiveButton("OK", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									finish();
								}
							})
							.create()
							.show();
				}
		}
		// Other 'case' lines to check for other
		// permissions this app might request.
	}

	private boolean getExecutionStatus()
	{
		final Switch start_stop_switch = (Switch) findViewById(R.id.start_stop_switch);
		return start_stop_switch.isChecked();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putBoolean(PREF_EXECUTION_STATUS, getExecutionStatus());
	}
	
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);
		final boolean isRunning = savedInstanceState.getBoolean(PREF_EXECUTION_STATUS, false);
		
		// set switch state to reflect service execution status
		Switch start_stop_switch = (Switch) findViewById(R.id.start_stop_switch);
        start_stop_switch.setChecked(isRunning);
	}

	/*
	private void stopService() {
		App.getAppContext().stopService(new Intent(App.getAppContext(), BackgroundRecorder.class));
	}
	*/
	
	public void handleMessage(Message msg)
	{
		Log.d(Constants.TAG, msg.obj.toString());
		liveView.setText(msg.obj.toString());
	}

	static class IncomingHandler extends Handler {
	    private final WeakReference<Main> mainActivity; 

	    IncomingHandler(Main activity) {
	        mainActivity = new WeakReference<Main>(activity);
	    }
	    
	    @Override
	    public void handleMessage(Message msg)
	    {
	         Main activity = mainActivity.get();
	         if (activity != null) {
	              activity.handleMessage(msg);
	         }
	    }
	} // end of IncomingHandler

		/*
	private void bindToService() {
		m_serviceConnection = new ServiceConnection() {

			@Override
			public void onServiceDisconnected(ComponentName name) {
				Log.d(Constants.TAG, "Unbound from service");
				m_serviceConnection = null;
			}

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				Log.d(Constants.TAG, "Bound to service");
				BackgroundRecorder recorder = ((BackgroundRecorder.BackgroundRecorderBinder) service).getService();
				m_startStopSwitch.setChecked(recorder.isRunning());

				Intent workIntent = recorder.getWorkIntent();
				if (null != workIntent) {
					Log.d(Constants.TAG, "Setting view controls");
					Bundle extras = workIntent.getExtras();
					cbGPS.setChecked(extras.getBoolean(Constants.EXTRA_GPS));
					cbBattery.setChecked(extras.getBoolean(Constants.EXTRA_BATTERY));
					cbBattery.setChecked(extras.getBoolean(Constants.EXTRA_SIGNAL_STRENGTH));
					cbSaveLog.setChecked(extras.getBoolean(Constants.EXTRA_SAVE_LOG));
					m_commentTxt.setText(extras.getString(Constants.EXTRA_COMMENT));
				}

				unbindService(m_serviceConnection);
				m_serviceConnection = null;
			}
		};

    	bindService(new Intent(this, BackgroundRecorder.class), m_serviceConnection, Context.BIND_AUTO_CREATE);
	}
	*/

	@Override
	protected void onDestroy()
	{
		/*
		if (m_serviceConnection != null) {
			unbindService(m_serviceConnection);
		}
		*/
		super.onDestroy();
	}
	
}; // end of Main