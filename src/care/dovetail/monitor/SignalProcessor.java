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
	private final int breathValues[] = new int[Config.BREATH_GRAPH_LENGTH];

	private final List<Integer> bpm = new ArrayList<Integer>();

	public int medianAmplitude;

	private final IirFilter breathFilter;
	private final IirFilter ecgFilter;

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
		// Lowpass 30bpm. frequency values relative to sampling rate. 0.0025 = 0.5Hz / 200Hz
		breathFilter = new IirFilter(IirFilterDesignFisher.design(
				FilterPassType.lowpass, FilterCharacteristicsType.bessel, 4, 0, 0.0025, 0));

		// Lowpass 840bpm. frequency values relative to sampling rate. 0.07 = 14Hz / 200Hz
		ecgFilter = new IirFilter(IirFilterDesignFisher.design(
				FilterPassType.lowpass, FilterCharacteristicsType.bessel, 4, 0, 0.07, 0));
	}

	public synchronized void update(int[] chunk) {
		updateCount++;

		System.arraycopy(values, chunk.length, values, 0, values.length - chunk.length);
		// Pass through a lowpass filter of 30bpm to get breath data
		for (int i = 0; i < chunk.length; i++) {
			values[(values.length - chunk.length) + i] = (int) ecgFilter.step(chunk[i]);
		}
		// System.arraycopy(chunk, 0, values, values.length - chunk.length, chunk.length);

		System.arraycopy(breathValues, chunk.length, breathValues, 0,
				breathValues.length - chunk.length);
		// Pass through a lowpass filter of 30bpm to get breath data
		for (int i = 0; i < chunk.length; i++) {
			breathValues[(breathValues.length - chunk.length) + i]
					= (int) breathFilter.step(chunk[i]);
		}

		if (updateCount == Config.FEATURE_DETECT_INTERVAL) {
			updateCount = 0;
			processFeatures();
		}
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

	public synchronized int[] getValues() {
		return values;
	}

	public synchronized int[] getBreathValues() {
		return breathValues;
	}

	public synchronized int getBpm() {
		Integer bpms[] = bpm.toArray(new Integer[0]);
		if (bpms.length < Config.MIN_BPM_SAMPLES) {
			return 0;
		}
		Arrays.sort(bpms);
		return bpms[bpms.length / 2];
	}

	private synchronized void processFeatures() {
		features.clear();
		findFeatures();
		updateStats();
		removeQrsOutliers();
		calculateBpm();
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

	private void updateStats() {
		int copyOfValues[] = values.clone();
		Arrays.sort(copyOfValues);
		medianAmplitude = copyOfValues[copyOfValues.length / 2];
	}

	private void calculateBpm() {
		List<Feature> qrss = getFeatures(Feature.Type.QRS);
		// With only 1 QRS we cannot calculate RR interval
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
			bpm.add(60000 / (medianInterval * Config.SAMPLE_INTERVAL_MS));
			if (bpm.size() > Config.MAX_BPM_SAMPLES) {
				bpm.remove(0);
			}
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
