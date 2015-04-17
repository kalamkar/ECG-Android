package care.dovetail.monitor;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;

public class Settings {
	public static final String TAG = "Settings";

	public static final String BT_REMOTE_ADDRESS = "BT_REMOTE_ADDRESS";
	public static final String AUDIO_THRESHOLD1 = "AUDIO_THRESHOLD1";
	public static final String AUDIO_THRESHOLD2 = "AUDIO_THRESHOLD2";


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
}