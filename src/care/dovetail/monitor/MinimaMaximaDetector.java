package care.dovetail.monitor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.util.Log;



public class MinimaMaximaDetector {
	private static final String TAG = "MinimaMaximaDetector";

	@SuppressLint("UseSparseArrays")
	public int[] process(int values[]) {
		if (values == null) {
			return null;
		}
		Map<Integer, Integer> minimas = new HashMap<Integer, Integer>();
		Map<Integer, Integer> maximas = new HashMap<Integer, Integer>();
		int lastMinima = Integer.MAX_VALUE;
		int lastMinimaIndex = -1;
		int lastMaxima = 0;
		int lastMaximaIndex = -1;
		for (int i = 0; i < values.length; i++) {
			if (values[i] < lastMinima ) {
				lastMinima = values[i];
				lastMinimaIndex = i;
			}
			if (values[i] > lastMaxima ) {
				int diff = lastMinimaIndex - lastMinimaIndex;
				if (diff > 0) {
					minimas.put(lastMinimaIndex, lastMinima);
					maximas.put(lastMaximaIndex, lastMaxima);
				}
				lastMaxima = values[i];
				lastMaximaIndex = i;
			}
		}
		minimas.putAll(maximas);
		Integer indexes[] = minimas.keySet().toArray(new Integer[0]);
		int processed[] = new int[indexes.length];
		for (int i = 0; i < indexes.length; i++) {
			processed[i] = minimas.get(indexes[i]);
		}
		Log.v(TAG, Arrays.toString(processed));
		return processed;
	}
}