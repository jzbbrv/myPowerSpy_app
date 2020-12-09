package com.securitylab.getbatterylevel;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;

public class BackgroundRecorder extends Service {
	private ListenerThread m_listenerThread;
	private NetworkThread m_networkThread;
	private boolean m_running = false;
	private Intent m_workIntent = null;
	private static final int NOTIFICATION_ID = 77;
	public boolean cbOnePhoneSetup, cbGPS, cbBattery;
	public String comment;


	private final IBinder m_binder = new BackgroundRecorderBinder();

	public class BackgroundRecorderBinder extends Binder {
		BackgroundRecorder getService() {
			return BackgroundRecorder.this;
		}
	}

	public BackgroundRecorder() {
		super();
	}

	/*
	public boolean isRunning() {
		return m_running;
	}

	public Intent getWorkIntent() {
		return m_workIntent;
	}
	*/

	@Override
	public int onStartCommand(Intent workIntent, int flags, int startId) {
		if (m_running) {
			return START_REDELIVER_INTENT;
		}
		makeForegroundService();
		m_workIntent = workIntent;

		Bundle extras = m_workIntent.getExtras();
		cbOnePhoneSetup = extras.getBoolean(Constants.EXTRA_ONEPHONE);
		cbGPS = extras.getBoolean(Constants.EXTRA_GPS);
		cbBattery = extras.getBoolean(Constants.EXTRA_BATTERY);
		comment = extras.getString(Constants.EXTRA_COMMENT);

		startListenerThread();
		Log.d(Constants.TAG, "Started listener thread");

		/*
		if (cbOnePhoneSetup
				|| cbBattery //comment out this line in battery mode to get battery consumption profile without any other running processes
		) {
			startNetworkThread();
			Log.d(Constants.TAG, "Started network thread");
		}
		*/

		m_running = true;
		return START_REDELIVER_INTENT;
	}

	private void startListenerThread() {
				m_listenerThread = new ListenerThread(this, cbOnePhoneSetup, cbGPS, cbBattery, comment);
		m_listenerThread.start();
	}

	private void startNetworkThread() {
		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		if (tm.getSimState() != TelephonyManager.SIM_STATE_ABSENT) {
			m_networkThread = new NetworkThread();
			m_networkThread.start();
			Log.d(Constants.TAG, "Started Network thread");
		}
	}

	private void makeForegroundService() {
		//create notification channel
		CharSequence name = getString(R.string.notification_channel_name);
		String description = getString(R.string.notification_channel_description);
		int importance = NotificationManager.IMPORTANCE_DEFAULT;
		NotificationChannel channel = new NotificationChannel("BackgroundService", name, importance);
		channel.setDescription(description);
		// Register the channel with the system; you can't change the importance
		// or other notification behaviors after this
		NotificationManager notificationManager = getSystemService(NotificationManager.class);
		notificationManager.createNotificationChannel(channel);

		//create notification for foreground service foreground service
		Intent notificationIntent = new Intent(this, BackgroundRecorder.class);
		PendingIntent pendingIntent =
				PendingIntent.getActivity(this, 0, notificationIntent, 0);

		Notification notification =
				new Notification.Builder(this, "BackgroundService")
						.setContentTitle(getText(R.string.notification_title))
						.setContentText(getText(R.string.notification_text))
						.setSmallIcon(R.mipmap.ic_stat_onesignal_default)
						.setContentIntent(pendingIntent)
						.setTicker(getText(R.string.notification_text))
						.build();

		//start BackgroundRecorder as foreground service
		startForeground(NOTIFICATION_ID, notification);
	}

	@Override
	public void onDestroy() {
		Log.d(Constants.TAG, "Service is quitting.");

		try {
			if (m_listenerThread != null) {
				m_listenerThread.quit();
				m_listenerThread.join();
			}
			if (m_networkThread != null) {
				m_networkThread.quit();
				m_networkThread.join();
			}
		} catch (InterruptedException e) {
			Log.e(Constants.TAG, "Waiting for threads interrupted");
			e.printStackTrace();
		}
		stopForeground(true);
		m_running = false;

		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return m_binder;
	}
}