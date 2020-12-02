package com.securitylab.getbatterylevel;

public class Constants {
	// Hardware status files
	public static final String TAG = "GetBatteryLevel";
	public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
	public static final int LOG_INTERVAL = 10000;

	//keep wakelock active for max. 1 hour
	public static final long WAKE_LOCK_TIMEOUT = 3600000;

	// Intent extras
	public static final String EXTRA_GPS = "com.securitylab.getbatterylevel.cbGPS";
	public static final String EXTRA_SIGNAL_STRENGTH = "com.securitylab.getbatterylevel.cbSignalStrength";
	public static final String EXTRA_BATTERY = "com.securitylab.getbatterylevel.cbBattery";
	public static final String EXTRA_SAVE_LOG = "com.securitylab.getbatterylevel.cbSaveLog";
	public static final String EXTRA_COMMENT = "com.securitylab.getbatterylevel.commentTxt";

	public static final String EXTRA_RECORD =  "com.securitylab.getbatterylevel.RECORD";
	public static final String ACTION_SEND_RECORD = "com.securitylab.getbatterylevel.SEND_RECORD";

}
