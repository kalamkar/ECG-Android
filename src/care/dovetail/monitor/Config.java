package care.dovetail.monitor;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class Config {
	public static final String BT_SENSOR_DATA_CHAR_PREFIX = "1902";
	public static final String BT_DEVICE_NAME = "Dovetail1";

	public static final int SAMPLE_RATE = 200;
	public static final int SAMPLE_INTERVAL_MS = 10;

	public static final int UI_UPDATE_INTERVAL_MILLIS = 10000;

	public static final SimpleDateFormat EVENT_TIME_FORMAT =
			new SimpleDateFormat("hh:mm:ssaa, MMM dd yyyy", Locale.US);
}
