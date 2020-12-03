package com.securitylab.getbatterylevel;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;

public class ListenerThread extends Thread {
	private static final int LOCATION_MIN_DISTANCE = 1;
	private static final int LOCATION_MIN_TIME = 500;
	private static final int THREAD_SLEEP_TIME = 10;
	private static final String LOG_EXT = ".csv";
	private int signalstrength, batlevel, batvolt, battemp, batamp, cellId, lac = 0;
	private int mccmnc = 26201;
	private double latitude, longitude = 0;
	private boolean gpsMode;
	private boolean batteryMode;
	private boolean m_execute = true;

	private Service m_service;
	private TelephonyManager m_telephonyMgr;
	private LocationManager mLocationManager;
	private BroadcastReceiver batteryReceiver;
	private IntentFilter batteryFilter;
	private String m_outputFilename;
	private PrintWriter m_outputWriter;
	private String comment;
	private PowerManager pm;
	private BatteryManager bm;
	private final Handler updateHandler = new Handler();
	private Timer logTimer;
	private TimerTask gpsTimerTask;
	private TimerTask batteryTimerTask;
	private GsmCellLocation gsmCellLocation;
	private SignalStrength mSignalStrength;
	private List<CellInfo> cellInfoList;
	private Executor updateCellInfoExecutor;

	public ListenerThread(Service service, boolean GPS_mode, boolean battery_mode, String comment) {
		super("ListenerThread");
		m_service = service;
		this.gpsMode = GPS_mode;
		this.batteryMode = battery_mode;
		this.comment = comment;
		
		pm = (PowerManager) App.getAppContext().getSystemService(Context.POWER_SERVICE);
		bm = (BatteryManager) App.getAppContext().getSystemService(Context.BATTERY_SERVICE);
	}

	@SuppressLint("Wakelock")
	public void run() {
		Log.d(Constants.TAG, "Starting Thread....");
		createOutputFile();
		startListening();
		//comment
		while (m_execute) {
			try {
				Thread.sleep(THREAD_SLEEP_TIME);
			} catch (InterruptedException e) {
				m_execute = false;
			}
		}
		Log.d(Constants.TAG, "Stopping Thread....");
	}

	private void createOutputFile() {
		final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY);
		final String currentDateandTime = sdf.format(new Date());

		String tmpText = comment;
		if (!tmpText.equals("")) {
			tmpText += "-";
		}
		tmpText += currentDateandTime;

		File outputFile;
		try {
			int i = 0;
			do {
				m_outputFilename = tmpText + LOG_EXT;
				Log.d(Constants.TAG, "tmpText: " + tmpText);
				outputFile = new File(App.getAppContext().getExternalFilesDir(null) + "/", m_outputFilename);
				++i;
				tmpText = comment + "-" + i;
			} while (outputFile.exists());
			Log.d(Constants.TAG, outputFile.getAbsolutePath());
		} catch (NullPointerException e) {
			Log.e(Constants.TAG, e.getMessage());
			e.printStackTrace();
		}
		writeHeader();
	}

	private void writeHeader() {
		try {
			if (m_outputWriter == null) {
				m_outputWriter = new PrintWriter(new BufferedWriter(new FileWriter(App.getAppContext().getExternalFilesDir(null) + "/" + m_outputFilename, false)));
				Log.d(Constants.TAG, "created outputWriter.");
			}
			String str = String.format("Time\tSOD\tVolt\tCurrent\tTemp\tSignal\tLatitude\tLongitude\tMCCMNC\tLAC\tCellID");
			m_outputWriter.println(str);
			m_outputWriter.flush();
		} catch (IOException e) {
			Log.v(Constants.TAG, e.getMessage());
			e.printStackTrace();
		}
	}

	public void startListening() {
		m_telephonyMgr = (TelephonyManager) m_service.getSystemService(Context.TELEPHONY_SERVICE);
		mLocationManager = (LocationManager) m_service.getSystemService(BackgroundRecorder.LOCATION_SERVICE);
		gsmCellLocation = new GsmCellLocation();
		logTimer = new Timer(true);

		if (gpsMode) {
			try {
				mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_MIN_TIME, LOCATION_MIN_DISTANCE,
						onLocationChange, Looper.getMainLooper());
			} catch (SecurityException e) {
				Log.e(Constants.TAG, e.getMessage());
				e.printStackTrace();
			}
			createGpsTimerTask();
		} else if (batteryMode) {
			lac = gsmCellLocation.getLac();
			cellId = gsmCellLocation.getCid();

			if (Build.VERSION.SDK_INT < 29) {
				m_telephonyMgr.listen(myPhoneStateListener,
						PhoneStateListener.LISTEN_CELL_INFO);
			}

			batteryReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					batlevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
					battemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
					batvolt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
					Log.d(Constants.TAG, "read from battery.");
				}
			};

			batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
			App.getAppContext().registerReceiver(batteryReceiver, batteryFilter);
			createBatteryTimerTask();
		}
	}

	public void createGpsTimerTask() {
		gpsTimerTask = new TimerTask() {
			@Override
			public void run() {
				updateLog();
			}
		};
		startTimer(gpsTimerTask);
	}

	public void createBatteryTimerTask() {
		batteryTimerTask = new TimerTask() {
			@Override
			public void run() {
				batamp = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000;
				try {
					if (Build.VERSION.SDK_INT >= 29) {
						m_telephonyMgr.requestCellInfoUpdate(App.getAppContext().getMainExecutor(), new TelephonyManager.CellInfoCallback() {
							@Override
							public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
								Log.d(Constants.TAG, "CellInfoCallback()");
								cellInfoList = cellInfo;
								handleCellInfoList();
								updateLog();
							}
						});
					} else {
						m_telephonyMgr.getAllCellInfo();
					}
				} catch (SecurityException e) {
					Log.e(Constants.TAG, "getAllCellInfo() failed due to a SecurityException.");
				}
			}
		};
		startTimer(batteryTimerTask);
	}

	public void startTimer(TimerTask task) {
		long currentTime = System.currentTimeMillis();
		long startTime = ((System.currentTimeMillis() / 10000) + 1) * 10000;
		logTimer.scheduleAtFixedRate(task, startTime - currentTime, Constants.LOG_INTERVAL);
		Log.d(Constants.TAG, "current time: " + currentTime + ", recording will start at " + startTime);
	}

	private void handleCellInfoList() {
		Log.d(Constants.TAG, "in handleCellInfoList().");
		Log.d(Constants.TAG, "length of CellInfoList: " + cellInfoList.size());
		for (CellInfo cell : cellInfoList) {
			if (cell.isRegistered()) {
				Log.d(Constants.TAG, "found registered cell");
				if (cell instanceof CellInfoLte) {
					signalstrength = ((CellInfoLte) cell).getCellSignalStrength().getDbm();
					lac = ((CellInfoLte) cell).getCellIdentity().getTac();
					cellId = ((CellInfoLte) cell).getCellIdentity().getCi();
					Log.d(Constants.TAG, "timestamp: " + cell.getTimeStamp());
				}
				else if (cell instanceof CellInfoGsm) {
					signalstrength = ((CellInfoGsm) cell).getCellSignalStrength().getDbm();
					lac = ((CellInfoGsm) cell).getCellIdentity().getLac();
					cellId = ((CellInfoGsm) cell).getCellIdentity().getCid();
					Log.d(Constants.TAG, "timestamp: " + cell.getTimeStamp());
				}
			}
		}
	}

	private void updateLog()
	{
		Log.d(Constants.TAG, "updating log...");
		long currTime = System.currentTimeMillis();

		//send record string to UI
    	String record = "Time " + currTime + "\nLevel " + batlevel +
    			"\nVolt " + batvolt + "\nAmp " + batamp + "\nTemp " + battemp
    			+ "\nSignal strength " + signalstrength
    			+ "\nLatitude " + latitude + "\nLongtitude " + longitude + "\nMCCMNC " + mccmnc + "\nLAC " + lac +
    			"\nCell ID " + cellId + ".";
    	
		Message msg = Message.obtain();
		msg.obj = record;
		Main.m_inhandler.sendMessage(msg);
		
	    final String logLine = prepareLogLine(currTime);

	    if (m_outputWriter != null) {
			m_outputWriter.println(logLine);
			m_outputWriter.flush();
		}
	} // end of updateLog

	private String prepareLogLine(long currTime) {
		String s = currTime + "\t" + batlevel + "\t" + batvolt + "\t" + batamp + "\t" + battemp + "\t" + signalstrength
				+ "\t" + latitude + "\t" + longitude + "\t" + mccmnc + "\t" + lac + "\t" + cellId;
		return s;
	}

	public PhoneStateListener myPhoneStateListener = new PhoneStateListener() {
		@Override
		public void onCellInfoChanged(List<CellInfo> cellInfo) {
			super.onCellInfoChanged(cellInfo);
			Log.d(Constants.TAG, "onCellInfoChanged()");
			cellInfoList = cellInfo;
			handleCellInfoList();
			updateLog();
		}
	};

	public LocationListener onLocationChange = new LocationListener() {
		public void onLocationChanged(Location loc) {
			latitude = loc.getLatitude();
			longitude = loc.getLongitude();
		}
		@Override
		public void onProviderDisabled(String arg0) {
		}
		@Override
		public void onProviderEnabled(String arg0) {
		}
		@Override
		public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		}
	};

	public void quit() {
		Log.d(Constants.TAG, "handling stop.");
		if (batteryMode) {
			m_telephonyMgr.listen(myPhoneStateListener, PhoneStateListener.LISTEN_NONE);
			batteryTimerTask.cancel();
		}
		if (gpsMode) {
			mLocationManager.removeUpdates(onLocationChange);
			gpsTimerTask.cancel();
		}
		if (null != m_outputWriter) {
			m_outputWriter.close();
			m_outputWriter = null;
		}
		m_execute = false;
	}
}
