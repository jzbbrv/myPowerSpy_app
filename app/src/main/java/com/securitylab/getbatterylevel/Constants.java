package com.securitylab.getbatterylevel;

public class Constants {
	// Hardware status files
	public static final String TAG = "GetBatteryLevel";
	public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
	public static final int READ_INTERVAL = 1000;
	public static final int SEND_INTERVAL = 1000;
	public static final int LOG_INTERVAL = 1000;

	//network constants
	public static final String GOOGLE_DNS_SERVER = "8.8.8.8";
	public static final int SERVER_PORT = 53;
	public static final String CELLTYPEGSM = "GSM";
	public static final String CELLTYPELTE = "LTE";

	//mode selection constants
	public static final String ONEPHONE = "ONE";
	public static final String TWOPHONES_BAT = "TWO-BAT";
	public static final String TWOPHONES_INFO = "TWO-INFO";

	//keep wakelock active for max. 1 hour
	public static final long WAKE_LOCK_TIMEOUT = 3600000;

	// Intent extras
	public static final String EXTRA_ONEPHONE = "com.securitylab.getbatterylevel.cbOnePhoneSetup";
	public static final String EXTRA_GPS = "com.securitylab.getbatterylevel.cbGPS";
	public static final String EXTRA_BATTERY = "com.securitylab.getbatterylevel.cbBattery";
	public static final String EXTRA_COMMENT = "com.securitylab.getbatterylevel.commentTxt";
}
