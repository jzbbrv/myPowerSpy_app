/**
package com.securitylab.getbatterylevel;

import com.securitylab.getbatterylevel.App;
import com.securitylab.getbatterylevel.Main;
import com.securitylab.getbatterylevel.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

// Used to create a status bar notification
// indicating service running status 
public class ServiceNotification {
	public static final int NOTIFICATION_ID = 1;
	protected static ServiceNotification m_instance;
	private NotificationManager m_notificationMgr;
	
	protected ServiceNotification() {
		m_notificationMgr = 
				(NotificationManager) App.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
	}
	
	public static ServiceNotification instance() {
		if (m_instance == null) {
			m_instance = new ServiceNotification();
		}
		
		return m_instance;
	}
	
	public static Notification buildNotification(Context context)
	{
		Intent notificationIntent = new Intent(context, Main.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
		
		NotificationCompat.Builder notificationBuilder = 
				new NotificationCompat.Builder(App.getAppContext())
					.setSmallIcon(R.drawable.ic_launcher)
					.setContentTitle(App.getAppContext().getText(R.string.app_name))
					.setContentText(App.getAppContext().getText(R.string.ticker_text))
					.setContentIntent(pendingIntent);
		
		return notificationBuilder.build();
	}
	
	public void show() {
		m_notificationMgr.notify(NOTIFICATION_ID, buildNotification(App.getAppContext()));
	}
	
	public void hide() {
		m_notificationMgr.cancel(NOTIFICATION_ID);
	}
} // end of ServiceNotification class
*/