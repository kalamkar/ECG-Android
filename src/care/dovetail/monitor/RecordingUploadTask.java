package care.dovetail.monitor;

import java.io.File;
import java.io.RandomAccessFile;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

public class RecordingUploadTask extends AsyncTask<Void, Void, Integer> {
	private final static String TAG = "RecordingUploadTask";

	private final App app;
	private final String filename;

	public RecordingUploadTask(App app, String filename) {
		this.app = app;
		this.filename = filename;
	}

	@Override
	protected Integer doInBackground(Void... params) {
		try {
			byte data[] = readFileData(filename);
			String appName = app.getResources().getString(R.string.app_name);
			String tags = String.format("%s,%s,%s,%s",
					new File(filename).getName()
						.replace(appName + "-", "").replace(".raw", "").replace('_', ','),
					Build.MANUFACTURER,
					Build.MODEL,
					String.format("%dHz", 1000 / Config.SAMPLE_INTERVAL_MS));
			String url = String.format("%s?tags=%s", Config.RECORDING_URL, tags.replaceAll(" ", "+"));
			Pair<Integer, String> response = Utils.uploadFile(url, "application/binary", data);
			Log.d(TAG, String.format("%d: %s", response.first, response.second));
			return response.first;
		} catch(Exception ex) {
			Log.w(TAG, String.format("Error uploading %s", filename), ex);
		}
		return 500;
	}

	@Override
	protected void onPostExecute(Integer responseCode) {
		if (responseCode == 200) {
			app.removeFromUploadQueue(filename);
		}
		app.uploadRecordings();
	}

	private static byte[] readFileData(String filename) throws Exception {
		File file = new File(filename);
        byte data[] = new byte[(int) file.length()];
        RandomAccessFile f = new RandomAccessFile(file, "r");
        try {
        	f.readFully(data);
        } finally {
        	f.close();
        }
        return data;
	}
}
