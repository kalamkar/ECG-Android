package care.dovetail.monitor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;

public class App extends Application {
	private static final String TAG = "App";

	private static final String UPLOAD_QUEUE = "UPLOAD_QUEUE";
	private static final String RECORDING_POSITIONS = "RECORDING_POSITIONS";

	private final Set<String> uploadQueue = new HashSet<String>();
	private String recordingPositions;

	@Override
	public void onCreate() {
		super.onCreate();
		SharedPreferences prefs = getSharedPreferences(getPackageName(), Application.MODE_PRIVATE);

		uploadQueue.clear();
		uploadQueue.addAll(prefs.getStringSet(UPLOAD_QUEUE, uploadQueue));
		uploadRecordings();

		recordingPositions = prefs.getString(RECORDING_POSITIONS, Config.POSITION_TAGS);
	}

	public void removeFromUploadQueue(final String filename) {
		uploadQueue.remove(filename);
		saveUploadQueue();
	}

	public void addToUploadQueue(final String filename) {
		uploadQueue.add(filename);
		saveUploadQueue();
	}

	private void saveUploadQueue() {
		final Editor editor = getSharedPreferences(
				getPackageName(), Application.MODE_PRIVATE).edit();
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				editor.putStringSet(UPLOAD_QUEUE, uploadQueue);
				editor.commit();
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				uploadRecordings();
				super.onPostExecute(result);
			}
		}.execute();
	}

	public void uploadRecordings() {
		if (!uploadQueue.isEmpty()) {
			new RecordingUploadTask(this, uploadQueue.iterator().next()).execute();
		}
	}

	public String nextRecordingTags() {
		String positions[] = recordingPositions.split(",");
		if (positions == null || positions.length == 0) {
			recordingPositions = Config.POSITION_TAGS;
			positions = recordingPositions.split(",");
		} else if (positions.length == 1) {
			recordingPositions = Config.POSITION_TAGS;
		} else {
			recordingPositions =
					Utils.join(",", Arrays.copyOfRange(positions, 1, positions.length));
		}
		saveStringPreference(RECORDING_POSITIONS, recordingPositions);
		return positions[0];
	}

	public String peekRecordingTags() {
		return recordingPositions.split(",")[0];
	}

	public String getRecordingIndex() {
		int total = Config.POSITION_TAGS.split(",").length;
		int remaining = recordingPositions.split(",").length;
		return String.format("%d/%d", (total - remaining) + 1, total);
	}

	private void saveStringPreference(final String prefName, final String prefValue) {
		final Editor editor = getSharedPreferences(
				getPackageName(), Application.MODE_PRIVATE).edit();
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				editor.putString(prefName, prefValue);
				editor.commit();
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				uploadRecordings();
				super.onPostExecute(result);
			}
		}.execute();
	}
}
