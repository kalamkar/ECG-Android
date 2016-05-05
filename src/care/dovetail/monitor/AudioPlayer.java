package care.dovetail.monitor;

import java.util.List;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.util.Log;
import care.dovetail.monitor.SignalProcessor.Feature;

public class AudioPlayer extends AudioTrack {
	private static final String TAG = "AudioPlayer";

	public AudioPlayer() {
		super(AudioManager.STREAM_MUSIC,
				Config.AUDIO_PLAYBACK_RATE,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_8BIT,
				Config.GRAPH_LENGTH * Config.AUDIO_BYTES_PER_SAMPLE,
				AudioTrack.MODE_STREAM);
	}

	public void write(final SignalProcessor signals) {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				try {
					byte audio[] = getBytes(signals.getFeatures(Feature.Type.QRS));
					write(audio, 0, audio.length);
				} catch (Throwable t) {
					Log.e(TAG, t.getCause() != null
							? t.getCause().getMessage() : t.getMessage(), t);
				}
				return null;
			}
		}.execute();
	}

	private static byte[] getBytes(List<Feature> points) {
		byte bytes[] = new byte[Config.GRAPH_LENGTH * Config.AUDIO_BYTES_PER_SAMPLE];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) 128;
		}
		for (int i = 0; i < points.size(); i++) {
			for (int j = 0; j < Config.AUDIO_BYTES_PER_SAMPLE; j++) {
				int value = (int) (128 - Math.sin( Math.toRadians(30 / (j + 1)) ) * 255);
				bytes[points.get(i).index * Config.AUDIO_BYTES_PER_SAMPLE + j] = (byte) value;
			}
		}
		return bytes;
	}
}
