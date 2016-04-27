package care.dovetail.monitor;

import java.util.HashSet;
import java.util.Set;

import android.app.Application;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;

public class App extends Application {
	private static final String TAG = "App";

	private static final String UPLOAD_QUEUE = "UPLOAD_QUEUE";

	private final Set<String> uploadQueue = new HashSet<String>();

	@Override
	public void onCreate() {
		super.onCreate();
		uploadQueue.clear();
		uploadQueue.addAll(getSharedPreferences(getPackageName(), Application.MODE_PRIVATE)
				.getStringSet(UPLOAD_QUEUE, uploadQueue));
		uploadRecordings();
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
}
