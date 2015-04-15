package care.dovetail.monitor;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;

public class Settings {
	public static final String TAG = "Settings";

	private static final String BT_REMOTE_ADDRESS = "BT_REMOTE_ADDRESS";
	private static final String AUDIO_GAIN = "AUDIO_GAIN";
	private static final String AUDIO_THRESHOLD1 = "AUDIO_THRESHOLD1";
	private static final String AUDIO_THRESHOLD2 = "AUDIO_THRESHOLD2";
	private static final String SPEAKER = "SPEAKER";
	private static final String COMPRESS = "COMPRESS";


	private final Context context;

	public int numSamples;

	public Settings(Context context) {
		this.context = context;
	}

	public void setBluetoothAddress(String address) {
		setStringPref(BT_REMOTE_ADDRESS, address);
	}

	public String getBluetoothAddress() {
		return context.getSharedPreferences(context.getPackageName(), Application.MODE_PRIVATE)
				.getString(BT_REMOTE_ADDRESS, null);
	}

	public void setAudioGain(int gain) {
		setIntPref(AUDIO_GAIN, gain);
	}

	public int getAudioGain() {
		int gain = context.getSharedPreferences(context.getPackageName(), Application.MODE_PRIVATE)
				.getInt(AUDIO_GAIN, context.getResources().getInteger(R.integer.default_gain));
		return gain == 0 ? 1 : gain;
	}

	public void setAudioThreshold1(int threshold) {
		setIntPref(AUDIO_THRESHOLD1, threshold);
	}

	public int getAudioThreshold1() {
		return context.getSharedPreferences(context.getPackageName(), Application.MODE_PRIVATE)
				.getInt(AUDIO_THRESHOLD1,
						context.getResources().getInteger(R.integer.default_threshold1));
	}

	public void setAudioThreshold2(int threshold) {
		setIntPref(AUDIO_THRESHOLD2, threshold);
	}

	public int getAudioThreshold2() {
		return context.getSharedPreferences(context.getPackageName(), Application.MODE_PRIVATE)
				.getInt(AUDIO_THRESHOLD2,
						context.getResources().getInteger(R.integer.default_threshold2));
	}

	public void setSpeaker(boolean speaker) {
		setBoolPref(SPEAKER, speaker);
	}

	public boolean getSpeaker() {
		return context.getSharedPreferences(context.getPackageName(), Application.MODE_PRIVATE)
				.getBoolean(SPEAKER, false);
	}

	public void setCompress(boolean compress) {
		setBoolPref(COMPRESS, compress);
	}

	public boolean getCompress() {
		return context.getSharedPreferences(context.getPackageName(), Application.MODE_PRIVATE)
				.getBoolean(COMPRESS, true);
	}

	private void setStringPref(final String pref, String value) {
		final Editor editor = context.getSharedPreferences(
				context.getPackageName(), Application.MODE_PRIVATE).edit();
		new AsyncTask<String, Void, Void>() {
			@Override
			protected Void doInBackground(String... values) {
				if (values != null && values.length > 0) {
					editor.putString(pref, values[0]);
					editor.commit();
				}
				return null;
			}
		}.execute(value);
	}

	private void setIntPref(final String pref, Integer value) {
		final Editor editor = context.getSharedPreferences(
				context.getPackageName(), Application.MODE_PRIVATE).edit();
		new AsyncTask<Integer, Void, Void>() {
			@Override
			protected Void doInBackground(Integer... values) {
				if (values != null && values.length > 0) {
					editor.putInt(pref, values[0]);
					editor.commit();
				}
				return null;
			}
		}.execute(value);
	}

	private void setBoolPref(final String pref, boolean value) {
		final Editor editor = context.getSharedPreferences(
				context.getPackageName(), Application.MODE_PRIVATE).edit();
		new AsyncTask<Boolean, Void, Void>() {
			@Override
			protected Void doInBackground(Boolean... values) {
				if (values != null && values.length > 0) {
					editor.putBoolean(pref, values[0]);
					editor.commit();
				}
				return null;
			}
		}.execute(value);
	}
}