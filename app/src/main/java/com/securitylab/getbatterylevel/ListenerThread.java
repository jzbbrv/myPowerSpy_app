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
import android.telephony.CellLocation;
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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class ListenerThread extends Thread {
    private static final int LOCATION_MIN_DISTANCE = 10;
    private static final int LOCATION_MIN_TIME = 500;
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
    private TimerTask logTimerTask;
    private TimerTask packageTimerTask;
    private TimerTask cellInfoTimerTask;
    private TimerTask batteryTimerTask;
    private List<CellInfo> cellInfoList;
    private DatagramSocket s;
    private InetAddress server;

    private String celltype;
    private int signalstrength, batvolt, batamp, cellId, lac, mcc, mnc = 0;
    private double currentLatitude, currentLongitude, latitude, longitude = 0;
    private final boolean gpsAndCellInfoMode;
    private final boolean batteryMode;
    private final boolean onePhoneSetup;
    private boolean phoneStateListenerActive = false;

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
		/*
		while (m_execute) {
			try {
				Thread.sleep(THREAD_SLEEP_TIME);
			} catch (InterruptedException e) {
				m_execute = false;
			}
		}
		 */
    }

    private void createOutputFile() {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY);
        final String currentDateandTime = sdf.format(new Date());

        String fileName = "";
        if (this.onePhoneSetup) {
            fileName = Constants.ONEPHONE + "-";
        } else if (this.batteryMode) {
            fileName = Constants.TWOPHONES_BAT + "-";
        } else if (this.gpsAndCellInfoMode) {
            fileName = Constants.TWOPHONES_INFO + "-";
        }
        fileName += currentDateandTime + "-" + Build.MANUFACTURER.toUpperCase() + "_" + Build.MODEL.toUpperCase();
        if (!comment.equals("")) {
            fileName += "-" + comment;
        }
        fileName = fileName.replaceAll(" ", "_");

        File outputFile;
        try {
            int i = 0;
            do {
                m_outputFilename = fileName + LOG_EXT;
                Log.d(TAG, "fileName: " + fileName);
                outputFile = new File(App.getAppContext().getExternalFilesDir(null) + "/", m_outputFilename);
                ++i;
                fileName = comment + "-" + i;
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
            String str = "Time\tVolt\tCurrent\tSignal\tLatitude\tLongitude\tCellType\tMCC\tMNC\tLAC\tCellID";
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

        /*
        if (Build.VERSION.SDK_INT < 29) {
            m_telephonyMgr.listen(myPhoneStateListener, PhoneStateListener.LISTEN_CELL_INFO);
        }
        */

        if (onePhoneSetup) {
            getLocationUpdates();
            getBatteryUpdates();
            startCellAndGpsInfoTimer(0);
            startPackageTimer();
            startBatteryTimer(50);
            startLoggingTimer(250, false);
        } else if (gpsAndCellInfoMode) {
            getLocationUpdates();
            startCellAndGpsInfoTimer(0);
            startLoggingTimer(250, true);
        } else if (batteryMode) {
            getBatteryUpdates();
            startPackageTimer();
            startBatteryTimer(5);
            startLoggingTimer(100, true);
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
                batvolt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                Log.d(TAG, "updated battery voltage.");
            }
        };
        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        App.getAppContext().registerReceiver(batteryReceiver, batteryFilter);
    }

    public void startCellAndGpsInfoTimer(int delay) {
        cellInfoTimerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    if (Build.VERSION.SDK_INT < 29) {
                        cellInfoList = m_telephonyMgr.getAllCellInfo();
                    } else {
                        try {
                            /*
                             * on newer Android version calling getAllCellInfo does not invoke an update
                             * in the returned cell info, call requestCellInfoUpdate instead
                             */
                            m_telephonyMgr.requestCellInfoUpdate(App.getAppContext().getMainExecutor(), new TelephonyManager.CellInfoCallback() {
                                @Override
                                public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
                                    Log.d(TAG, "CellInfoCallback()");
                                    cellInfoList = cellInfo;
                                    handleCellInfoList();
                                }
                            });
                        } catch (Exception e) {
                            // handle ClassNotFoundException on LineageOS
                            cellInfoList = m_telephonyMgr.getAllCellInfo();
                            handleCellInfoList();
                        }
                    }
                    if (cellInfoList != null) {
                        handleCellInfoList();
                    } else {
                        /*
                         * In practice, some devices do not implement getAllCellInfo and
                         * reqeustCellInfoUpdate. herefore, getCellLocation() is used instead.
                         * CellLocation should return the serving cell, unless it is LTE (in which
                         * case it should return null). In practice, however, some devices do return
                         * LTE cells. Attention: As getCellLocation() is deprecated since API 26,
                         * it might be removed in future Android versions. To retrieve signal
                         * strength a PhoneStateListener is registered that listens for changes
                         * in the received signal strength. Attention: As LISTEN_SIGNAL_STRENGTH is
                         * deprecated, it might be removed in future Android versions.
                         */
                        m_telephonyMgr.listen(myPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTH);
                        phoneStateListenerActive = true;
                        CellLocation location = m_telephonyMgr.getCellLocation();
                        handleCellLocation(location);
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "getAllCellInfo() failed due to a SecurityException.");
                }
                latitude = currentLatitude;
                longitude = currentLongitude;
            }
        };
        startTimer(cellInfoTimerTask, delay, Constants.READ_INTERVAL);
    }

    public PhoneStateListener myPhoneStateListener = new PhoneStateListener() {
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            /*
             * getGsmSignalStrength returns signal strength in ASU, convert to dBm
             */
            signalstrength = -113 + 2 * signalStrength.getGsmSignalStrength();
        }
    };

    /**
     * sends out data package every 10 seconds to create dummy network traffic
     */
    public void startPackageTimer() {
        byte[] message = new byte[1000];
        final DatagramPacket p = new DatagramPacket(message, 1000, server, Constants.SERVER_PORT);

        packageTimerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    s.send(p);
                    Log.d(TAG, "send data package at " + System.currentTimeMillis());
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                    e.printStackTrace();
                }

            }
        };
        startTimer(packageTimerTask, 0, Constants.SEND_INTERVAL);
    }

    public void startBatteryTimer(int delay) {
        batteryTimerTask = new TimerTask() {
            @Override
            public void run() {
                //int currentBatAmp;
                batamp = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000;
				/*
				batamp += currentBatAmp;
				batvolt += currentBatVolt;
				batteryCount++;
				if (batteryCount % (10000 / Constants.READ_BATTERY_RATE) == 0) {
					averageBatAmp = batamp / (10000 / Constants.READ_BATTERY_RATE);
					averageBatVolt = batvolt / (10000 / Constants.READ_BATTERY_RATE);
					batamp = 0;
					batvolt = 0;
				}
				*/
            }
        };
        startTimer(batteryTimerTask, delay, Constants.READ_INTERVAL);
    }

    /**
     * log latest position every 10 seconds, delay to decide when to log, either in middle
     * or at the end of each interval
     *
     * @param delay         of start
     * @param logTimeIsNow: true when logged at end of 10 interval, false if logged in the middle
     */
    public void startLoggingTimer(int delay, final boolean logTimeIsNow) {
        logTimerTask = new TimerTask() {
            @Override
            public void run() {
                updateLog(logTimeIsNow);
            }
        };
        startTimer(logTimerTask, delay, Constants.LOG_INTERVAL);
    }

    public void startTimer(TimerTask task, int startTimeDelay, int rate) {
        long currentTime = System.currentTimeMillis();
        long startTime = (((System.currentTimeMillis() / 10000) + 1) * 10000) + startTimeDelay;
        Timer logTimer = new Timer(true);
        logTimer.scheduleAtFixedRate(task, startTime - currentTime, rate);
        Log.d(TAG, "current time: " + currentTime + ", recording will start at " + startTime);
    }

    private void handleCellInfoList() {
        Log.d(TAG, "in handleCellInfoList(), currentTime: " + System.currentTimeMillis());
        for (CellInfo cell : cellInfoList) {
            if (cell.isRegistered()) {
                if (cell instanceof CellInfoLte) {
                    signalstrength = ((CellInfoLte) cell).getCellSignalStrength().getDbm();
                    celltype = Constants.CELLTYPELTE;
                    lac = ((CellInfoLte) cell).getCellIdentity().getTac();
                    cellId = ((CellInfoLte) cell).getCellIdentity().getCi();
                    mcc = Integer.parseInt(((CellInfoLte) cell).getCellIdentity().getMccString());
                    mnc = Integer.parseInt(((CellInfoLte) cell).getCellIdentity().getMncString());
                    //Log.d(TAG, "timestamp: " + cell.getTimeStamp());
                } else if (cell instanceof CellInfoGsm) {
                    signalstrength = ((CellInfoGsm) cell).getCellSignalStrength().getDbm();
                    celltype = Constants.CELLTYPEGSM;
                    lac = ((CellInfoGsm) cell).getCellIdentity().getLac();
                    cellId = ((CellInfoGsm) cell).getCellIdentity().getCid();
                    mcc = Integer.parseInt(((CellInfoGsm) cell).getCellIdentity().getMccString());
                    mnc = Integer.parseInt(((CellInfoGsm) cell).getCellIdentity().getMncString());
                    //Log.d(TAG, "timestamp: " + cell.getTimeStamp());
                }
            }
        }
    }

    public void handleCellLocation(CellLocation location) {
        if (location instanceof GsmCellLocation) {
            celltype = "LTE or GSM";
            cellId = ((GsmCellLocation) location).getCid();
            lac = ((GsmCellLocation) location).getLac();
            // get MCC and MNC to identify used network
            String networkOperator = m_telephonyMgr.getNetworkOperator();
            mcc = Integer.parseInt(networkOperator.substring(0, 2));
            mnc = Integer.parseInt(networkOperator.substring(2));
        }
    }

    private void updateLog(boolean now) {
        Log.d(TAG, "updating log at " + System.currentTimeMillis());

        long logTime;
        if (now) {
            logTime = System.currentTimeMillis();
        } else {
            //logs data collected in interval 0 to 10 sec at sec 10 with current time from 5 sec ago
            logTime = System.currentTimeMillis() - 5000;
        }
        String record = "Time: " + logTime + "\nVolt: " + batvolt + "\nAmp: " + batamp +
                "\nSignal strength: " + signalstrength + "\nLatitude: " + latitude +
                "\nLongitude: " + longitude + "\nCellType: " + celltype + "\nID: " + mcc + " " + mnc + " " + lac +
                " " + cellId + ".";

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
                + "\t" + currentLatitude + "\t" + currentLongitude + "\t" + celltype + "\t" + mcc + "\t" + mnc + "\t" + lac + "\t" + cellId);
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
            if (phoneStateListenerActive) {
                m_telephonyMgr.listen(myPhoneStateListener, PhoneStateListener.LISTEN_NONE);
                phoneStateListenerActive = false;
            }
            App.getAppContext().unregisterReceiver(batteryReceiver);
            packageTimerTask.cancel();
            cellInfoTimerTask.cancel();
            logTimerTask.cancel();
            batteryTimerTask.cancel();
            mLocationManager.removeUpdates(onLocationChange);
        } else if (batteryMode) {
            if (phoneStateListenerActive) {
                m_telephonyMgr.listen(myPhoneStateListener, PhoneStateListener.LISTEN_NONE);
                phoneStateListenerActive = false;
            }
            App.getAppContext().unregisterReceiver(batteryReceiver);
            packageTimerTask.cancel();
            logTimerTask.cancel();
            batteryTimerTask.cancel();
        } else if (gpsAndCellInfoMode) {
            mLocationManager.removeUpdates(onLocationChange);
            cellInfoTimerTask.cancel();
            logTimerTask.cancel();
        }
        if (null != m_outputWriter) {
            m_outputWriter.close();
            m_outputWriter = null;
        }
    }
}