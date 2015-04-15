package care.dovetail.monitor;

import java.util.concurrent.ArrayBlockingQueue;

import android.util.Log;
import care.dovetail.monitor.BackgroundService.ValuesListener;

public class BeatCounter {
	private static final String TAG = "BeatCounter";


	private final ValuesListener listener;
	private final int threshold1;
	private final int threshold2;

	public BeatCounter(ValuesListener listener, int threshold1, int threshold2) {
		this.listener = listener;
		this.threshold1 = threshold1;
		this.threshold2 = threshold2;
	}

	private ArrayBlockingQueue<Long> beatTimes =
			new ArrayBlockingQueue<Long>(Config.HEART_BEAT_HISTORY);
	private long lastBeatTime = 0;

	public int getBeatsPerMinute() {
		long prevBeatTime = 0;
		int delaySum = 0;
		int delayCount = 0;
		for (Long beatTime : beatTimes) {
			if (prevBeatTime == 0) {
				prevBeatTime = beatTime;
				continue;
			}
			long delay = beatTime - prevBeatTime;
			if (delay < Config.MAX_HEART_BEAT_DELAY && delay > Config.MIN_HEART_BEAT_DELAY) {
				delaySum += delay;
				delayCount++;
			}
			prevBeatTime = beatTime;
		}
		if (delaySum == 0 || delayCount == 0) {
			return 0;
		}
		return  (60 * 1000) / (delaySum / delayCount);
	}

	public boolean process(float buffer[]) {
		final boolean hasHeartBeat = hasHeartBeat1(buffer, threshold1, threshold2);

		// Remove stale beats
		long currentTime = System.currentTimeMillis();
		Long firstBeatTime = beatTimes.peek();
		if (firstBeatTime != null && firstBeatTime
				< (currentTime - Config.UI_UPDATE_INTERVAL_MILLIS - Config.MAX_HEART_BEAT_DELAY)) {
			try {
				beatTimes.take();
			} catch (InterruptedException ex) {
				Log.w(TAG, ex);
			}
		}

		listener.onNewValues(buffer, hasHeartBeat);

		if (hasHeartBeat) {
			heartBeatDetected(currentTime);
		}

		return true;
	}

	private void heartBeatDetected(long currentTime) {
		// If this is a second sound of the heart beat (immediately following first), skip it.
		if (currentTime - lastBeatTime < Config.MIN_HEART_BEAT_DELAY) {
			return;
		}
		try {
			if (beatTimes.size() >= Config.HEART_BEAT_HISTORY) {
				beatTimes.take();
			}
			beatTimes.put(currentTime);
			lastBeatTime = currentTime;
			Log.v(TAG, String.format("Heartbeat time is %d", currentTime));
		} catch (InterruptedException ex) {
			Log.w(TAG, ex);
		}
	}

	private static boolean hasHeartBeat1(float buffer[], int threshold1, int threshold2) {
		boolean foundBeatPeak = false;
		boolean foundBeatValley = false;
		int numSamplesWithMin = 0;
		int numSamplesWithMax = 0;

		for (int i = 0; i < buffer.length; i++) {
			if (buffer[i] == -1.0) {
				numSamplesWithMin++;
			} else {
				if (numSamplesWithMin > threshold1 && numSamplesWithMin < threshold2) {
					foundBeatValley = true;
				}
				numSamplesWithMin = 0;
			}
			if (buffer[i] == 1.0) {
				numSamplesWithMax++;
			} else {
				if (numSamplesWithMax > threshold1 && numSamplesWithMax < threshold2) {
					foundBeatPeak = true;
				}
				numSamplesWithMax = 0;
			}
		}
		return foundBeatValley || foundBeatPeak;
	}
}