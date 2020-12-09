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
import android.os.Looper;
import android.os.Message;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ListenerThread extends Thread {
	private static final int LOCATION_MIN_DISTANCE = 1;
	private static final int LOCATION_MIN_TIME = 500;
	private static final int THREAD_SLEEP_TIME = 10;
	private static final String LOG_EXT = ".csv";
	private static final String TAG = "ListenerThread";

	private final Service m_service;
	private TelephonyManager m_telephonyMgr;
	private LocationManager mLocationManager;
	private BroadcastReceiver batteryReceiver;
	private String m_outputFilename;
	private PrintWriter m_outputWriter;
	private final String comment;
	private final BatteryManager bm;
	private Timer logTimer;
	private TimerTask logTimerTask;
	private TimerTask batteryTimerTask;
	private TimerTask cellInfoTimerTask;
	private List<CellInfo> cellInfoList;
	private DatagramSocket s;
	private InetAddress server;

	private int batteryCount = 0;
	private int averageBatAmp;
	private int averageBatVolt;
	private int currentBatVolt;
	private int signalstrength, batvolt, batamp, cellId, lac = 0;
	private double currentLatitude, currentLongitude, latitude, longitude = 0;
	private final boolean gpsAndCellInfoMode;
	private final boolean batteryMode;
	private final boolean onePhoneSetup;
	private boolean m_execute = true;

	public ListenerThread(Service service, boolean onePhoneSetup, boolean GPS_mode, boolean battery_mode, String comment) {
		super("ListenerThread");
		m_service = service;
		this.onePhoneSetup = onePhoneSetup;
		this.gpsAndCellInfoMode = GPS_mode;
		this.batteryMode = battery_mode;
		this.comment = comment;

		bm = (BatteryManager) App.getAppContext().getSystemService(Context.BATTERY_SERVICE);
	}

	@SuppressLint("Wakelock")
	public void run() {
		Log.d(TAG, "Starting Thread....");
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
				Log.d(TAG, "tmpText: " + tmpText);
				outputFile = new File(App.getAppContext().getExternalFilesDir(null) + "/", m_outputFilename);
				++i;
				tmpText = comment + "-" + i;
			} while (outputFile.exists());
			Log.d(TAG, outputFile.getAbsolutePath());
		} catch (NullPointerException e) {
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
		}
		writeHeader();
	}

	private void writeHeader() {
		try {
			if (m_outputWriter == null) {
				m_outputWriter = new PrintWriter(new BufferedWriter(new FileWriter(App.getAppContext().getExternalFilesDir(null) + "/" + m_outputFilename, false)));
				Log.d(TAG, "created outputWriter.");
			}
			String str = "Time\tVolt\tCurrent\tSignal\tLatitude\tLongitude\tMCCMNC\tLAC\tCellID";
			m_outputWriter.println(str);
			m_outputWriter.flush();
		} catch (IOException e) {
			Log.v(TAG, e.getMessage());
			e.printStackTrace();
		}
	}

	public void startListening() {
		m_telephonyMgr = (TelephonyManager) m_service.getSystemService(Context.TELEPHONY_SERVICE);
		mLocationManager = (LocationManager) m_service.getSystemService(BackgroundRecorder.LOCATION_SERVICE);

		try {
			s = new DatagramSocket();
			server = InetAddress.getByName(Constants.GOOGLE_DNS_SERVER);
		} catch (Exception e) {
			e.printStackTrace();
		}

		logTimer = new Timer(true);

		if (Build.VERSION.SDK_INT < 29) {
			m_telephonyMgr.listen(myPhoneStateListener,
					PhoneStateListener.LISTEN_CELL_INFO);
		}

		if (onePhoneSetup) {
			getLocationUpdates();
			startCellAndGpsInfoTimer();
			getBatteryUpdates();
			startBatteryTimer();
			startLogging(10000, false);
		} else if (gpsAndCellInfoMode) {
			getLocationUpdates();
			startCellAndGpsInfoTimer();
			startLogging(5050, true);
		} else if (batteryMode) {
			getBatteryUpdates();
			startBatteryTimer();
			startLogging(10000, false);
		}
	}

	public void getLocationUpdates() {
		try {
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_MIN_TIME, LOCATION_MIN_DISTANCE,
					onLocationChange, Looper.getMainLooper());
		} catch (SecurityException e) {
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * starts Listener that get updates whenever the battery's voltage changes
	 */
	public void getBatteryUpdates() {
		batteryReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				currentBatVolt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
				Log.d(TAG, "updated battery voltage.");
			}
		};
		IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		App.getAppContext().registerReceiver(batteryReceiver, batteryFilter);
	}

	public void startCellAndGpsInfoTimer() {
		cellInfoTimerTask = new TimerTask() {
			@Override
			public void run() {
				try {
					//lac = gsmCellLocation.getLac();
					//cellId = gsmCellLocation.getCid();
					if (Build.VERSION.SDK_INT < 29) {
						m_telephonyMgr.listen(myPhoneStateListener,
								PhoneStateListener.LISTEN_CELL_INFO);
						//request cell info, callback caught with myPhoneStateListener
						m_telephonyMgr.getAllCellInfo();
					} else {
						m_telephonyMgr.requestCellInfoUpdate(App.getAppContext().getMainExecutor(), new TelephonyManager.CellInfoCallback() {
							@Override
							public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
								Log.d(TAG, "CellInfoCallback()");
								cellInfoList = cellInfo;
								handleCellInfoList();
							}
						});
					}
				} catch (SecurityException e) {
					Log.e(TAG, "getAllCellInfo() failed due to a SecurityException.");
				}
				latitude = currentLatitude;
				longitude = currentLongitude;
			}
		};
		startTimer(cellInfoTimerTask, 5000, 10000);
	}

	public PhoneStateListener myPhoneStateListener = new PhoneStateListener() {
		@Override
		public void onCellInfoChanged(List<CellInfo> cellInfo) {
			super.onCellInfoChanged(cellInfo);
			Log.d(TAG, "onCellInfoChanged()");
			cellInfoList = cellInfo;
			handleCellInfoList();
		}
	};

	/**
	 * obtains latest amp of battery every second after
	 * sending out a data package and calculates average
	 * for the 10 sec interval
	 */
	public void startBatteryTimer() {
		batteryTimerTask = new TimerTask() {
			@Override
			public void run() {
				try {
					//send data package and read out battery consumption
					byte[] message = new byte[1000];
					int currentBatAmp;
					DatagramPacket p = new DatagramPacket(message, 1000, server, Constants.SERVER_PORT);
					//long start = System.currentTimeMillis();
					s.send(p);
					currentBatAmp = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000;
					//long stop = System.currentTimeMillis();
					//Log.d(TAG, "elapsed time between start of sending and reading out battery stats: " + (stop - start));

					//Update battery stats
					batamp += currentBatAmp;
					batvolt += currentBatVolt;
					batteryCount++;
				} catch (Exception e) {
					Log.d(TAG, e.getMessage());
					e.printStackTrace();
				}
				if (batteryCount % (10000 / Constants.READ_BATTERY_RATE) == 0) {
					averageBatAmp = batamp / (10000 / Constants.READ_BATTERY_RATE);
					averageBatVolt = batvolt / (10000 / Constants.READ_BATTERY_RATE);
					batamp = 0;
					batvolt = 0;
				}
			}
		};
		startTimer(batteryTimerTask, 0 , Constants.READ_BATTERY_RATE);
	}

	public void startTimer(TimerTask task, int startTimeDelay, int rate) {
		long currentTime = System.currentTimeMillis();
		long startTime = (((System.currentTimeMillis() / 10000) + 1) * 10000) + startTimeDelay;
		logTimer.scheduleAtFixedRate(task, startTime - currentTime, rate);
		Log.d(TAG, "current time: " + currentTime + ", recording will start at " + startTime);
	}

	private void handleCellInfoList() {
		Log.d(TAG, "in handleCellInfoList(), currentTime: " + System.currentTimeMillis());
		for (CellInfo cell : cellInfoList) {
			if (cell.isRegistered()) {
				if (cell instanceof CellInfoLte) {
					signalstrength = ((CellInfoLte) cell).getCellSignalStrength().getDbm();
					lac = ((CellInfoLte) cell).getCellIdentity().getTac();
					cellId = ((CellInfoLte) cell).getCellIdentity().getCi();
					//Log.d(TAG, "timestamp: " + cell.getTimeStamp());
				}
				else if (cell instanceof CellInfoGsm) {
					signalstrength = ((CellInfoGsm) cell).getCellSignalStrength().getDbm();
					lac = ((CellInfoGsm) cell).getCellIdentity().getLac();
					cellId = ((CellInfoGsm) cell).getCellIdentity().getCid();
					//Log.d(TAG, "timestamp: " + cell.getTimeStamp());
				}
			}
		}
	}

	/**
	 * log latest position every 10 seconds, delay to decide when to log, either in middle
	 * or at the end of each interval
	 * @param delay of start
	 * @param logTimeIsNow: true when logged at end of 10 interval, false if logged in the middle
	 */
	public void startLogging(int delay, final boolean logTimeIsNow) {
		logTimerTask = new TimerTask() {
			@Override
			public void run() {
				updateLog(logTimeIsNow);
			}
		};
		startTimer(logTimerTask, delay, 10000);
	}

	private void updateLog(boolean now)
	{
		Log.d(TAG, "updating log at " + System.currentTimeMillis());

		long logTime;
		if (now) {
			logTime = System.currentTimeMillis();
		} else {
			//logs data collected in interval 0 to 10 sec at sec 10 with current time from 5 sec ago
			logTime = System.currentTimeMillis() - 5000;
		}
    	String record = "Time " + logTime + "\nVolt " + averageBatVolt + "\nAmp " + averageBatAmp +
				"\nSignal strength " + signalstrength + "\nLatitude " + latitude +
				"\nLongitude " + longitude + "\nMCCMNC " + Constants.MCCMNC + "\nLAC " + lac +
    			"\nCell ID " + cellId + ".";

		//send record string to UI
		Message msg = Message.obtain();
		msg.obj = record;
		Main.m_inhandler.sendMessage(msg);
		
	    final String logLine = prepareLogLine(logTime);
	    if (m_outputWriter != null) {
			m_outputWriter.println(logLine);
			m_outputWriter.flush();
		}
	}

	private String prepareLogLine(long currTime) {
		return (currTime + "\t" + batvolt + "\t" + batamp + "\t" + signalstrength
				+ "\t" + currentLatitude + "\t" + currentLongitude + "\t" + Constants.MCCMNC + "\t" + lac + "\t" + cellId);
	}

	public LocationListener onLocationChange = new LocationListener() {
		public void onLocationChanged(Location loc) {
			currentLatitude = loc.getLatitude();
			currentLongitude = loc.getLongitude();
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
		Log.d(TAG, "handling stop.");
		if (onePhoneSetup) {
			m_telephonyMgr.listen(myPhoneStateListener, PhoneStateListener.LISTEN_NONE);
			App.getAppContext().unregisterReceiver(batteryReceiver);
			batteryTimerTask.cancel();
			cellInfoTimerTask.cancel();
			logTimerTask.cancel();
			batteryCount = 0;
			mLocationManager.removeUpdates(onLocationChange);
			logTimerTask.cancel();
		} else if (batteryMode) {
			m_telephonyMgr.listen(myPhoneStateListener, PhoneStateListener.LISTEN_NONE);
			App.getAppContext().unregisterReceiver(batteryReceiver);
			batteryTimerTask.cancel();
			logTimerTask.cancel();
			batteryCount = 0;
		} else if (gpsAndCellInfoMode) {
			mLocationManager.removeUpdates(onLocationChange);
			cellInfoTimerTask.cancel();
			logTimerTask.cancel();
		}
		if (null != m_outputWriter) {
			m_outputWriter.close();
			m_outputWriter = null;
		}
		m_execute = false;
	}
}
