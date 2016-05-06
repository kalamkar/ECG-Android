package care.dovetail.monitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.util.Pair;
import biz.source_code.dsp.filter.FilterCharacteristicsType;
import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.IirFilterDesignFisher;
import care.dovetail.monitor.SignalProcessor.Feature.Type;

public class SignalProcessor {
	private static final String TAG = "NewSignalProcessor";

	private static final int WINDOW_SIZE = 120 / Config.SAMPLE_INTERVAL_MS; // 120ms max QRS time
	private static final int MIN_QRS_HEIGHT = 50;
	private static final int MIN_NUM_INTERVALS_FOR_BPM = 3;
	private static final float QRS_AMPLITUDE_TOLERANCE = 0.3f;  // 30% tolerance

	private final int windowSize = WINDOW_SIZE;
	private final int minQrsHeight = MIN_QRS_HEIGHT;

	private final List<Feature> features = new ArrayList<Feature>();

	private int updateCount = 0;
	private final int values[] = new int[Config.GRAPH_LENGTH];

	public int medianAmplitude;
	public int bpm;

	private IirFilter filter;

	public static class Feature {
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

		public Feature(Type type, int start, int end, int[] data) {
			this.type = type;
			this.start = start;
			this.end = end;
			this.data = data;

			index = start + (data.length / 2);
		}

		@Override
		public boolean equals(Object other) {
			if (other == null || !(other instanceof Feature)) {
				return false;
			}
			Feature otherPt = (Feature) other;
			return type == otherPt.type && start == otherPt.start && end == otherPt.end;
		}
	}

	public SignalProcessor() {
		filter = new IirFilter(IirFilterDesignFisher.design(
				FilterPassType.lowpass, FilterCharacteristicsType.bessel, 4, 0, 0.1, 0));
	}

	public void update(int[] chunk) {
		updateCount++;
		System.arraycopy(values, chunk.length, values, 0, values.length - chunk.length);
		System.arraycopy(chunk, 0, values, values.length - chunk.length, chunk.length);
		if (updateCount < Config.GRAPH_UPDATE_COUNT) {
			return;
		}
		updateCount = 0;
		processFeatures();
	}

	private synchronized void processFeatures() {
		features.clear();
		resetStats();
		findFeatures();
		updateStats();
		removeQrsOutliers();
		calculateBpm();
	}

	public synchronized List<Feature> getFeatures(Type type) {
		List<Feature> subset = new ArrayList<Feature>();
		for (Feature fp : features) {
			if (type != null && type.equals(fp.type)) {
				subset.add(fp);
			}
		}
		return subset;
	}

	public int[] getValues() {
		return values;
	}

	private void findFeatures() {
		Feature lastAddedQrs = null;
		for (int i = 0; i < values.length - windowSize; i++) {
			Feature qrs = getQrs(i, i + windowSize);
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

	private Feature getQrs(int windowStart, int windowEnd) {
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
			Feature feature = new Feature(Feature.Type.QRS, windowStart, windowEnd,
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
		bpm = 0;
	}

	private void updateStats() {
		int copyOfValues[] = values.clone();
		Arrays.sort(copyOfValues);
		medianAmplitude = copyOfValues[copyOfValues.length / 2];
	}

	private void calculateBpm() {
		List<Feature> qrss = getFeatures(Feature.Type.QRS);
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
		List<Feature> qrss = getFeatures(Feature.Type.QRS);
		if (qrss.size() > 2) {
			int median = getMedianQrsHeight(qrss);
			for (Feature qrs : qrss) {
				if (qrs.height < median - (median * QRS_AMPLITUDE_TOLERANCE)
						|| qrs.height > median + (median * QRS_AMPLITUDE_TOLERANCE)) {
					features.remove(qrs);
				}
			}
		}
	}

	private int getMedianQrsHeight(List<Feature> qrs) {
		int heights[] = new int[features.size()];
		for (int i = 0; i < qrs.size(); i++) {
			heights[i] = qrs.get(i).height;
		}
		Arrays.sort(heights);
		return heights[heights.length / 2];
	}
}
