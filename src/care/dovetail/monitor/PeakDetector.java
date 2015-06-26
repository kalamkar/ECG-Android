package care.dovetail.monitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.util.Log;
import android.util.Pair;

public class PeakDetector {
	private static final String TAG = "PeakDetector";

	private final int windowSize;
	private final int halfWindow;
	private final int minSlope;

	public PeakDetector(int windowSize, int minSlope) {
		this.windowSize = windowSize;
		this.halfWindow = windowSize / 2;
		this.minSlope = minSlope;
	}

	public List<Pair<Integer, Integer>> findPeaks(int[] values) {
		List<Pair<Integer, Integer>> peaks = new ArrayList<Pair<Integer, Integer>>();

		for (int i = 0; i < values.length; i++) {
	        int left = i >= halfWindow  ? i - halfWindow : 0;
	        int right = i <= values.length - halfWindow ? i + halfWindow : values.length -1;
	        left = right >= halfWindow ? left : windowSize - right;

	        if (isPeak(values, i) && isSpike(values[i], Arrays.copyOfRange(values, left, right)) ) {

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
		}

		return peaks;
	}

	private boolean isPeak(int[] values, int index) {
		if (index > 0 && index < values.length - 1) {
			return values[index - 1] <= values[index] && values[index] >= values[index + 1];
		}
		return false;
	}

	private boolean isSpike(int value, int[] window) {
		int sum = 0;
		for (int num : window) {
			sum += num;
		}
		int average = (sum - value) / (window.length -1);
		return value - average > minSlope;
	}
}
