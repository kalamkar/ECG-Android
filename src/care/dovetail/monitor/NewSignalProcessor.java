package care.dovetail.monitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.util.Pair;
import care.dovetail.monitor.NewSignalProcessor.FeaturePoint.Type;

public class NewSignalProcessor {
	private static final String TAG = "NewSignalProcessor";

	private static final int WINDOW_SIZE = 120 / Config.SAMPLE_INTERVAL_MS; // 120ms max QRS time
	private static final int MIN_QRS_HEIGHT = 50;
	private static final int MIN_NUM_INTERVALS_FOR_BPM = 5;
	private static final float QRS_AMPLITUDE_TOLERANCE = 0.3f;  // 30% tolerance

	private final int windowSize = WINDOW_SIZE;
	private final int minQrsHeight = MIN_QRS_HEIGHT;

	private final List<FeaturePoint> features = new ArrayList<FeaturePoint>();

	private int values[];

	public int minAmplitude;
	public int medianAmplitude;
	public int maxAmplitude;
	public int signalDurationMillis;

	public int bpm;

	public static class FeaturePoint {
		public enum Type {
			QRS
		}

		public final Type type;
		public final int start;
		public final int end;
		public final int[] data;

		public final int index;

		public int min;
		public int max;
		public int height;

		public FeaturePoint(Type type, int start, int end, int[] data) {
			this.type = type;
			this.start = start;
			this.end = end;
			this.data = data;

			index = start + (data.length / 2);
		}

		@Override
		public boolean equals(Object other) {
			if (other == null || !(other instanceof FeaturePoint)) {
				return false;
			}
			FeaturePoint otherPt = (FeaturePoint) other;
			return type == otherPt.type && start == otherPt.start && end == otherPt.end;
		}
	}

	public void update(int[] values) {
		features.clear();
		resetStats();

		if (values == null || values.length == 0) {
			return;
		}
		this.values = values;
		findFeaturePoints();
		updateStats();
		removeQrsOutliers();
		calculateBpm();
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
		FeaturePoint lastAddedQrs = null;
		for (int i = 0; i < values.length - windowSize; i++) {
			FeaturePoint qrs = getQrs(i, i + windowSize);
			if (qrs == null) {
				continue;
			}
			if (lastAddedQrs == null) {
				features.add(qrs);
				lastAddedQrs = qrs;
			} else if (lastAddedQrs.index < qrs.index - (windowSize * 2)) {
				features.add(qrs);
				lastAddedQrs = qrs;
			} else if (lastAddedQrs.height < qrs.height) {
				features.remove(lastAddedQrs);
				features.add(qrs);
				lastAddedQrs = qrs;
			}
		}
	}

	private FeaturePoint getQrs(int windowStart, int windowEnd) {
		Pair<Integer, Integer> min = Pair.create(windowStart, values[windowStart]);
		Pair<Integer, Integer> max = Pair.create(windowStart, values[windowStart]);
		for (int i = windowStart; i < windowEnd; i++) {
			if (values[i] < min.second) {
				min = Pair.create(i, values[i]);
			}
			if (values[i] > max.second) {
				max = Pair.create(i, values[i]);
			}
		}
		if (Math.abs(max.second - min.second) > minQrsHeight) {
			FeaturePoint feature = new FeaturePoint(FeaturePoint.Type.QRS, windowStart, windowEnd,
					Arrays.copyOfRange(values, windowStart, windowEnd));
			feature.min = min.second;
			feature.max = max.second;
			feature.height = Math.abs(max.second - min.second);
			return feature;
		}
		return null;
	}

	private void resetStats() {
		medianAmplitude = 0;
		signalDurationMillis = 0;
		bpm = 0;
	}

	private void updateStats() {
		int copyOfValues[] = values.clone();
		Arrays.sort(copyOfValues);
		minAmplitude = copyOfValues[0];
		medianAmplitude = copyOfValues[copyOfValues.length / 2];
		maxAmplitude = copyOfValues[copyOfValues.length -1];
		signalDurationMillis =  values.length * Config.SAMPLE_INTERVAL_MS;
	}

	private void calculateBpm() {
		List<FeaturePoint> qrss = getFeaturePoints(FeaturePoint.Type.QRS);
		if (qrss.size() < 2) {
			return;
		}
		int indices[] = new int[qrss.size()];
		for (int i = 0; i < qrss.size(); i++) {
			indices[i] = qrss.get(i).index;
		}
		Arrays.sort(indices);
		int intervals[] = new int[indices.length - 1];
		for (int i = 0; i < indices.length - 1; i++) {
			intervals[i] = indices[i+1] - indices[i];
		}
		// Update BPM only when there are sufficient number of RR intervals.
		if (intervals.length < MIN_NUM_INTERVALS_FOR_BPM) {
			return;
		}
		Arrays.sort(intervals);
		int medianInterval = intervals[intervals.length / 2];
		if (medianInterval != 0) {
			bpm = 60000 / (medianInterval * Config.SAMPLE_INTERVAL_MS);
		}
	}

	private void removeQrsOutliers() {
		List<FeaturePoint> qrss = getFeaturePoints(FeaturePoint.Type.QRS);
		if (qrss.size() > 2) {
			int median = getMedianQrsHeight(qrss);
			for (FeaturePoint qrs : qrss) {
				if (qrs.height < median - (median * QRS_AMPLITUDE_TOLERANCE)
						|| qrs.height > median + (median * QRS_AMPLITUDE_TOLERANCE)) {
					features.remove(qrs);
				}
			}
		}
	}

	private int getMedianQrsHeight(List<FeaturePoint> qrs) {
		int heights[] = new int[features.size()];
		for (int i = 0; i < qrs.size(); i++) {
			heights[i] = qrs.get(i).height;
		}
		Arrays.sort(heights);
		return heights[heights.length / 2];
	}
}
