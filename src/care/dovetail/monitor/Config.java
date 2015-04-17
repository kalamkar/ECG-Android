package care.dovetail.monitor;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class Config {
	public static final String SERVICE_EVENT = "care.dovetail.babymonitor.ServiceEvent";
	public static final String SERVICE_DATA = "care.dovetail.babymonitor.ServiceData";

	public static final String EVENT_TYPE = "SERVICE_EVENT_TYPE";
	public static final String EVENT_TIME = "SERVICE_EVENT_TIME";
	public static final String SENSOR_DATA = "SENSOR_DATA";
	public static final String SENSOR_DATA_HEARTBEAT = "SENSOR_DATA_HEARTBEAT";

	public static final String BT_SENSOR_DATA_CHAR_PREFIX = "1902";

	public static final String API_URL = "https://dovetail-api.appspot.com";
	public static final String DEVICE_URL = API_URL + "/device";
	public static final String EVENT_URL  = API_URL + "/event";

	public static final String GCM_SENDER_ID = "209668780940";

	public static final int EVENT_SYNC_INTERVAL_MILLIS = 60 * 1000;

	public static final int SAMPLE_RATE = 100;

	public static final int UI_UPDATE_INTERVAL_MILLIS = 10000;

	public static final int MIN_HEART_BEAT_DELAY = 200;
	public static final int MAX_HEART_BEAT_DELAY = 2000;
	public static final int HEART_BEAT_HISTORY =
			Config.UI_UPDATE_INTERVAL_MILLIS / MIN_HEART_BEAT_DELAY;

	public static final SimpleDateFormat EVENT_TIME_FORMAT =
			new SimpleDateFormat("hh:mm:ssaa, MMM dd yyyy", Locale.US);
}
