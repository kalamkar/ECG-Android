package care.dovetail.monitor;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class Config {

	public static final String API_URL = "http://dovetail-data-1.appspot.com";
	public static final String RECORDING_URL = API_URL + "/recording";

	public static final long DATA_UUID = 0x404846A1;
	public static final String BT_DEVICE_NAME_PREFIX = "Dovetail";
	public static final int SAMPLE_INTERVAL_MS = 5;

	public static final int AUDIO_PLAYBACK_RATE = 4000; // 4kHz
	public static final int AUDIO_BYTES_PER_SAMPLE =
			AUDIO_PLAYBACK_RATE / (1000 / SAMPLE_INTERVAL_MS);

	public static final int GRAPH_LENGTH = 1000;	// 5 seconds at 200Hz

	public static final int SHORT_GRAPH_MIN = 0; 	//  64 for V2,  64 for V1
	public static final int SHORT_GRAPH_MAX = 275; 	// 192 for V2, 255 for V1

	public static final int LONG_GRAPH_MIN = 100;		// 100 for V2, 100 for V1
	public static final int LONG_GRAPH_MAX = 255;		// 192 for V2, 255 for V1

	public static final int GRAPH_UPDATE_MILLIS = 500;

	// Detect features every 10 chunk updates (and not every update)
	public static final int FEATURE_DETECT_INTERVAL = 10;

	public static final int BPM_UPDATE_MILLIS = 3000;
	public static final int MIN_BPM_SAMPLES = 5;
	public static final int MAX_BPM_SAMPLES = 10;

	public static final SimpleDateFormat EVENT_TIME_FORMAT =
			new SimpleDateFormat("hh:mm:ssaa, MMM dd yyyy", Locale.US);

	public static final String POSITION_TAGS =
			"top,right,bottom,left,top_far,right_far,bottom_far,left_far";
}
