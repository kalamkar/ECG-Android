package care.dovetail.monitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;
import care.dovetail.monitor.OldSignalProcessor.FeaturePoint.Type;

public class OldSignalProcessor {
	private static final String TAG = "SignalProcessor";

	private static final int WINDOW_SIZE = 100;
	private static final int MINIMUM_SLOPE = 80;

	private final int windowSize = WINDOW_SIZE;
	private final int halfWindow = windowSize / 2;
	private final int minSlope = MINIMUM_SLOPE;

	private final List<FeaturePoint> features = new ArrayList<FeaturePoint>();

	private int values[];

	public int minAmplitude;
	public int medianAmplitude;
	public int maxAmplitude;
	public int signalDurationMillis;

	public Map<FeaturePoint.Type, Integer> bpm = new HashMap<FeaturePoint.Type, Integer>();
	public Map<FeaturePoint.Type, Integer> count = new HashMap<FeaturePoint.Type, Integer>();
	public Map<FeaturePoint.Type, Integer> avgAmp = new HashMap<FeaturePoint.Type, Integer>();
	public Map<FeaturePoint.Type, Integer> sumAmp = new HashMap<FeaturePoint.Type, Integer>();
	public Map<FeaturePoint.Type, Integer> sumDistance = new HashMap<FeaturePoint.Type, Integer>();
	public Map<FeaturePoint.Type, Integer> totalDuration = new HashMap<FeaturePoint.Type, Integer>();

	public static class FeaturePoint {
		public enum Type {
			PEAK,
			VALLEY
		}

		public Type type;
		public int index;
		public int amplitude;
		public int slope;

		public FeaturePoint(Type type, int index, int amplitude) {
			this.type = type;
			this.index = index;
			this.amplitude = amplitude;
		}

		@Override
		public boolean equals(Object other) {
			if (other == null || !(other instanceof FeaturePoint)) {
				return false;
			}
			FeaturePoint otherPt = (FeaturePoint) other;
			return index == otherPt.index && type == otherPt.type;
		}
	}

	public void update(int[] values) {
		features.clear();
		resetStats();

		if (values == null || values.length == 0) {
			return;
		}
		this.values = values;

		int copyOfValues[] = values.clone();
		Arrays.sort(copyOfValues);
		minAmplitude = copyOfValues[0];
		medianAmplitude = copyOfValues[copyOfValues.length / 2];
		maxAmplitude = copyOfValues[copyOfValues.length -1];
		signalDurationMillis =  values.length * Config.SAMPLE_INTERVAL_MS;

		findFeaturePoints();
		updateStats();
	}

	public List<FeaturePoint> getFeaturePoints(Type type) {
		List<FeaturePoint> points = new ArrayList<FeaturePoint>();
		for (FeaturePoint fp : features) {
			if (type != null && type.equals(fp.type)) {
				points.add(fp);
			}
		}
		return points;
	}

	private void findFeaturePoints() {
		for (int i = 0; i < values.length; i++) {
	        int left = i >= halfWindow  ? i - halfWindow : 0;
	        int right = i <= values.length - halfWindow ? i + halfWindow : values.length -1;
	        left = right >= halfWindow ? left : windowSize - right;

	        if (isPeak(i) && isSpike(i, left, right)) {
	        	FeaturePoint prevPeak = findPreviousFeature(Type.PEAK, i - halfWindow);
	        	if (prevPeak != null) {
	        		Log.v(TAG, String.format("Peak within window of %d with val %d, %d val = %d",
	        				i, values[i], prevPeak.index, prevPeak.amplitude));
	                // Successive peak, keep only highest
	                if (prevPeak.amplitude < values[i]) {
	                	Log.v(TAG, "replacing peak");
	                	features.remove(prevPeak);
	                	features.add(new FeaturePoint(Type.PEAK, i, values[i]));
	                }
	                continue;
	        	}
	        	features.add(new FeaturePoint(Type.PEAK, i, values[i]));
	        }

	        if (isValley(i) && isSpike(i, left, right)) {
	        	FeaturePoint prevValley = findPreviousFeature(Type.VALLEY, i - halfWindow);
	        	if (prevValley != null) {
	                Log.v(TAG, String.format("Valley within window of %d with val %d, %d val = %d",
	                		i, values[i], prevValley.index, prevValley.amplitude));

	                // Successive valley, keep only lowest
	                if (prevValley.amplitude > values[i]) {
	                	Log.v(TAG, "replacing valley");
	                	features.remove(prevValley);
	                	features.add(new FeaturePoint(Type.VALLEY, i, values[i]));
	                }
	                continue;
	        	}
	        	features.add(new FeaturePoint(Type.VALLEY, i, values[i]));
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

	private boolean isSpike(int index, int windowStart, int windowEnd) {
		int sum = 0;
		int value = values[index];
		for (int i = windowStart; i < windowEnd; i++) {
			sum += values[i];
		}
		int average = (sum - value) / (windowEnd - windowStart -1);
		return Math.abs(value - average) > minSlope;
	}

	private void resetStats() {
		medianAmplitude = 0;
		signalDurationMillis = 0;

		for (Type type : FeaturePoint.Type.values()) {
			count.put(type, 0);
			sumAmp.put(type, 0);
			sumDistance.put(type, 0);
			bpm.put(type, 0);
			avgAmp.put(type, 0);
			totalDuration.put(type, 0);
		}
	}

	private void updateStats() {
		Map<FeaturePoint.Type, Integer> prevIndex = new HashMap<FeaturePoint.Type, Integer>();
		Map<FeaturePoint.Type, Integer> firstIndex = new HashMap<FeaturePoint.Type, Integer>();
		Map<FeaturePoint.Type, Integer> lastIndex = new HashMap<FeaturePoint.Type, Integer>();
		for (FeaturePoint fp : features) {
			count.put(fp.type, count.get(fp.type) + 1);
			sumAmp.put(fp.type, sumAmp.get(fp.type) + Math.abs(fp.amplitude - medianAmplitude));
			if (prevIndex.containsKey(fp.type)) {
				sumDistance.put(fp.type, sumDistance.get(fp.type) + fp.index - prevIndex.get(fp.type));
			}
			prevIndex.put(fp.type, fp.index);
			if (!firstIndex.containsKey(fp.type)) {
				firstIndex.put(fp.type, fp.index);
			}
			lastIndex.put(fp.type, fp.index);
		}

		for (Type type : FeaturePoint.Type.values()) {
			int totalDistance = 0;
			if (lastIndex.containsKey(type) && firstIndex.containsKey(type)) {
				totalDistance = lastIndex.get(type) - firstIndex.get(type);
			}

			int duration = totalDistance * Config.SAMPLE_INTERVAL_MS;
			int avgDistance = count.get(type) > 1 ? sumDistance.get(type) / (count.get(type) - 1) : 0;
			int avgCount = avgDistance == 0 ? 0 : totalDistance / avgDistance;

			totalDuration.put(type, duration);
			bpm.put(type, duration == 0 ? 0 : 60 * 1000 * avgCount / duration);
			avgAmp.put(type, count.get(type) == 0 ? 0 :
				(sumAmp.get(type) / count.get(type)) - medianAmplitude);
		}
	}

	private FeaturePoint findPreviousFeature(Type type, int minIndex) {
		for (int i = features.size() - 1; i >= 0; i--) {
			FeaturePoint fp = features.get(i);
			if (type == fp.type && fp.index > minIndex) {
				return fp;
			}
		}
		return null;
	}
}
