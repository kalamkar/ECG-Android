package care.dovetail.monitor;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.app.DownloadManager;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Log;

public class EcgDataWriter {
	private static final String TAG = "EcgDataWriter";

	private static final SimpleDateFormat FILE_NAME_FORMAT =
			new SimpleDateFormat("MMM-dd-kk-mm-ss", Locale.US);

    private final MainActivity activity;
    private File file;
    private BufferedOutputStream output;

	private Timer recordingTimer = null;
	private long recordingStartTime = 0;

	public EcgDataWriter(MainActivity activity, String fileTags) {
		this.activity = activity;
		File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		file = new File(dir,
				String.format("%s-%s_%s.raw", activity.getResources().getString(R.string.app_name),
				FILE_NAME_FORMAT.format(new Date()), fileTags));
        try {
        	file.createNewFile();
        	output = new BufferedOutputStream(new FileOutputStream(file));
        } catch (Exception e){
        	Log.e(TAG, "Error opening output RAW file.", e);
        }
        recordingStartTime = System.currentTimeMillis();
		recordingTimer = new Timer();
		recordingTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				EcgDataWriter.this.activity.onRecordingUpdate(getDurationSeconds());
			}
		}, 0, 1000);
	}

	public boolean write(int data[]) {
		try {
			for (int value : data) {
				output.write(value);
			}
			output.flush();
		} catch (IOException e) {
			Log.e(TAG, "Error writing to RAW output file.", e);
		}
		return true;
	}

	public long getDurationSeconds() {
		return (System.currentTimeMillis() - recordingStartTime) / 1000;
	}

	public void close() {
		recordingTimer.cancel();
		try {
    	    output.close();
    	    ((App) activity.getApplication()).addToUploadQueue(file.getAbsolutePath());
    	    DownloadManager downloads =
    	    		(DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
    	    downloads.addCompletedDownload(file.getName(), "ECG Raw Data", true,
    	    		"application/x-binary", file.getAbsolutePath(), file.length(), false);
            MediaScannerConnection.scanFile(activity, new String[] { file.getAbsolutePath() }, null,
            		null);
        } catch (Exception e){
        	Log.e(TAG, "Error closing output stream and codec.", e);
        }
	}
}
