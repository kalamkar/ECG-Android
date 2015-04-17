package care.dovetail.monitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.util.Log;


public class BeatCounter {
	private static final String TAG = "BeatCounter";

	private final int threshold1;
	private final int threshold2;

	private int processed[];
	private int bpm;

	public BeatCounter(int threshold1, int threshold2) {
		this.threshold1 = threshold1;
		this.threshold2 = threshold2;
	}

	public int getBeatsPerMinute() {
		return bpm;
	}

	@SuppressLint("UseSparseArrays")
	public boolean process(int values[]) {
		processed = Arrays.copyOf(values, values.length);

		int sorted[] = Arrays.copyOf(values, values.length);
		Arrays.sort(sorted);
		int median = values.length % 2 == 0 ? (sorted[(int) Math.floor(values.length / 2)]
				+ sorted[(int) Math.ceil(values.length / 2)]) /2 : sorted[values.length / 2];
		int max = sorted[sorted.length - 1];
		int min = sorted[0];
		Log.i(TAG, String.format("Min = %d, Median = %d, Max = %d", min, median, max));

		List<Integer> indices = new ArrayList<Integer>();
		for (int i = 0; i < values.length; i++) {
			if (values[i] > (max * 0.9)) {
				processed[i] = values[i];
				indices.add(i);
			} else {
				processed[i] = 0;
			}
		}
		bpm = calculateBpm(indices, values.length - 1);

		return false;
	}

	public int[] getProcessed() {
		return processed;
	}

	private static int calculateBpm(List<Integer> indices, int maxIndex) {
		if (indices.size() <= 2) {
			Log.e(TAG, "Insufficeint indices");
			return 0;
		}

		int sum = 0;
		for (int i = 1; i < indices.size(); i++) {
			sum += indices.get(i) - indices.get(i-1);
		}
		int average = sum / (indices.size() -1);

//		// If first or last index falls outside of tolerance then its not good waveform
//		if (indices.get(0) > average * 1.1) {
//			Log.e(TAG, String.format("First distance %d falls outside of average %d.",
//					indices.get(0), average));
//			return -1;
//		}
//		if (indices.get(indices.size() - 1) < maxIndex - (average * 1.1)) {
//			Log.e(TAG, String.format("Last distance %d falls outside of average %d.",
//					indices.get(indices.size() - 1), average));
//			return -1;
//		}

		List<Integer> filtered = new ArrayList<Integer>();
		filtered.add(indices.get(0));
		for (int i = 1; i < indices.size(); i++) {
			// If current index is close to previous ignore it
			if (indices.get(i-1) > indices.get(i) - (average * 0.2)) {
				continue;
			}
			filtered.add(indices.get(i));
		}

		sum = 0;
		for (int i = 1; i < filtered.size(); i++) {
			sum += filtered.get(i) - filtered.get(i-1);
		}
		average = sum / (filtered.size() -1);

		for (int i = 1; i < filtered.size(); i++) {
			int distance = filtered.get(i) - filtered.get(i-1);
			if (distance < average * 0.8 || distance > average * 1.2) {
				// Distance falls outside of 10% tolerance of average distance
				Log.e(TAG, String.format("Distance %d falls outside of average %d.",
						distance, average));
				return -1;
			}
		}
		return Math.round((60 * Config.SAMPLE_RATE) / (sum / filtered.size()));
	}
}