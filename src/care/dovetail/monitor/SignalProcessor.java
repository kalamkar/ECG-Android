package care.dovetail.monitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.util.Log;
import android.util.Pair;

public class SignalProcessor {
	private static final String TAG = "PeakDetector";

	private final int windowSize;
	private final int halfWindow;
	private final int minSlope;

	public final List<Pair<Integer, Integer>> peaks = new ArrayList<Pair<Integer, Integer>>();
	public final List<Pair<Integer, Integer>> valleys = new ArrayList<Pair<Integer, Integer>>();

	private int values[];

	public int bpm;
	public int peakCount;
	public int avgPeakAmplitude;
	public int medianAmplitude;
	public int peaksDurationMillis;
	public int signalDurationMillis;

	public SignalProcessor(int windowSize, int minSlope) {
		this.windowSize = windowSize;
		this.halfWindow = windowSize / 2;
		this.minSlope = minSlope;
	}

	public void update(int[] values) {
		peaks.clear();
		valleys.clear();
		resetStats();

		if (values == null) {
			return;
		}
		this.values = values;

		int copyOfValues[] = values.clone();
		Arrays.sort(copyOfValues);
		medianAmplitude = copyOfValues[copyOfValues.length / 2];

		findPeaksValleys();
		updateStats();
	}

	private void findPeaksValleys() {
		for (int i = 0; i < values.length; i++) {
	        int left = i >= halfWindow  ? i - halfWindow : 0;
	        int right = i <= values.length - halfWindow ? i + halfWindow : values.length -1;
	        left = right >= halfWindow ? left : windowSize - right;

	        int window[] = Arrays.copyOfRange(values, left, right);
	        if (isPeak(i) && isSpike(values[i], window, true)) {

	        	if (peaks.size() > 0) {
	        		Pair<Integer, Integer> prevPeak = peaks.get(peaks.size() -1);

		            if (prevPeak.first > (i - halfWindow)) {
		                Log.i(TAG, String.format("Peak within window of %d with val %d, %d val = %d",
		                		i, values[i], prevPeak.first, prevPeak.second));

		                // Successive peak, keep only highest
		                if (prevPeak.second < values[i]) {
		                	Log.i(TAG, "replacing peak");
		                	peaks.remove(prevPeak);
		                	peaks.add(Pair.create(i, values[i]));
		                }
		                continue;
		            }
	        	}
	        	peaks.add(Pair.create(i, values[i]));
	        }

	        if (isValley(i) && isSpike(values[i], window, false)) {
	        	if (valleys.size() > 0) {
	        		Pair<Integer, Integer> prevValley = valleys.get(valleys.size() -1);

		            if (prevValley.first > (i - halfWindow)) {
		                Log.i(TAG, String.format("Valley within window of %d with val %d, %d val = %d",
		                		i, values[i], prevValley.first, prevValley.second));

		                // Successive valley, keep only lowest
		                if (prevValley.second > values[i]) {
		                	Log.i(TAG, "replacing valley");
		                	valleys.remove(prevValley);
		                	valleys.add(Pair.create(i, values[i]));
		                }
		                continue;
		            }
	        	}
	        	valleys.add(Pair.create(i, values[i]));
	        }
		}
	}

	private boolean isPeak(int index) {
		if (index > 0 && index < values.length - 1) {
			return values[index - 1] <= values[index] && values[index] >= values[index + 1];
		}
		return false;
	}

	private boolean isValley(int index) {
		if (index > 0 && index < values.length - 1) {
			return values[index - 1] >= values[index] && values[index] <= values[index + 1];
		}
		return false;
	}

	private boolean isSpike(int value, int[] window, boolean isPeak) {
		int sum = 0;
		for (int num : window) {
			sum += num;
		}
		int average = (sum - value) / (window.length -1);
		return isPeak ? value - average > minSlope : average - value > minSlope;
	}

	private void resetStats() {
		bpm = 0;
		peakCount = 0;
		avgPeakAmplitude = 0;
		medianAmplitude = 0;
		peaksDurationMillis = 0;
		signalDurationMillis = 0;
	}

	private void updateStats() {
		peakCount = peaks.size();
		if (peaks.size() == 0) {
			return;
		} else if (peaks.size() == 1) {
			avgPeakAmplitude = peaks.get(0).second - medianAmplitude;
			return;
		}
		int ampSum = 0;
		int distanceSum = 0;
		int prevIndex = -1;
		for (Pair<Integer,Integer> peak : peaks) {
			ampSum += peak.second;
			distanceSum += prevIndex >= 0 ? peak.first - prevIndex : 0;
			prevIndex = peak.first;
		}
		int totalPeaksDistance = peaks.get(peaks.size() - 1).first - peaks.get(0).first;
		peaksDurationMillis =  totalPeaksDistance * Config.SAMPLE_INTERVAL_MS;

		int avgPeakDistance = distanceSum / (peaks.size() - 1);
		int avgPeakCount = avgPeakDistance == 0 ? 0 : totalPeaksDistance / avgPeakDistance;

		// Returns BPM and Average amplitude
		bpm = 60 * 1000 * avgPeakCount / peaksDurationMillis;
		avgPeakAmplitude = (ampSum / peaks.size()) - medianAmplitude;
	}
}
