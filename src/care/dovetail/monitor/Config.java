package care.dovetail.monitor;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class Config {

	public static final String API_URL = "http://dovetail-data-1.appspot.com";
	public static final String RECORDING_URL = API_URL + "/recording";

	public static final long DATA_UUID = 0x404846A1;
	public static final String BT_DEVICE_NAME_PREFIX = "Dovetail";
	public static final int SAMPLE_INTERVAL_MS = 1;

	public static final int GRAPH_LENGTH = 2000;
	public static final int LONG_TERM_GRAPH_LENGTH = 6000; // 5 per second hence 600 = 2 minutes

	public static final int SHORT_GRAPH_MIN = 0; 	//  64 for V2,  64 for V1
	public static final int SHORT_GRAPH_MAX = 255; 	// 192 for V2, 255 for V1

	public static final int LONG_GRAPH_MIN = 100;		// 100 for V2, 100 for V1
	public static final int LONG_GRAPH_MAX = 255;		// 192 for V2, 255 for V1

	public static final int GRAPH_UPDATE_MILLIS = 200;
	public static final int GRAPH_UPDATE_COUNT = (100 * GRAPH_UPDATE_MILLIS) / GRAPH_LENGTH;

	public static final SimpleDateFormat EVENT_TIME_FORMAT =
			new SimpleDateFormat("hh:mm:ssaa, MMM dd yyyy", Locale.US);
}
