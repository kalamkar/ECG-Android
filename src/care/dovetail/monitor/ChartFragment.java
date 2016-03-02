package care.dovetail.monitor;

import java.util.ArrayList;
import java.util.List;

import android.app.Fragment;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import care.dovetail.monitor.ChartView.Type;
import care.dovetail.monitor.SignalProcessor.FeaturePoint;

public class ChartFragment extends Fragment {
	private static final String TAG = "ChartFragment";

	private ChartView ecg;
	private ChartView peaks;
	private ChartView valleys;
	private ChartView median;
	private ChartView breath;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_chart, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		ecg = ((ChartView) getView().findViewById(R.id.ecg));
		ecg.setColor(Color.BLUE);

		peaks = ((ChartView) getView().findViewById(R.id.peaks));
		peaks.setType(Type.POINT);
		peaks.setSize(5);
		peaks.setColor(getResources().getColor(android.R.color.holo_orange_dark));

		valleys = ((ChartView) getView().findViewById(R.id.valleys));
		valleys.setType(Type.POINT);
		valleys.setSize(5);
		valleys.setColor(getResources().getColor(android.R.color.holo_blue_dark));

		median = ((ChartView) getView().findViewById(R.id.median));
		median.setColor(getResources().getColor(android.R.color.darker_gray));

		breath = ((ChartView) getView().findViewById(R.id.breath));
		breath.setColor(getResources().getColor(android.R.color.holo_green_light));
	}

	public void updateGraph(int data[]) {
		List<Pair<Integer, Integer>> points = new ArrayList<Pair<Integer, Integer>>(data.length);
		for (int i = 0; i < data.length; i++) {
			points.add(Pair.create(i, data[i]));
		}
		ecg.setData(points);
	}

	public void updateLongGraph(int data[]) {
		List<Pair<Integer, Integer>> points = new ArrayList<Pair<Integer, Integer>>(data.length);
		for (int i = 0; i < data.length; i++) {
			points.add(Pair.create(i, data[i]));
		}
		breath.setData(points);
	}

	public void updateMarkers(List<FeaturePoint> peaks, List<FeaturePoint> valleys,
			int medianAmplitude) {
		List<Pair<Integer, Integer>> medianPoints = new ArrayList<Pair<Integer, Integer>>(2);
		medianPoints.add(Pair.create(0, medianAmplitude));
		medianPoints.add(Pair.create(Config.GRAPH_LENGTH - 1, medianAmplitude));
		median.setData(medianPoints);

//		double ratio = Config.NUM_SAMPLES_LONG_TERM_GRAPH / Config.GRAPH_LENGTH;
		List<Pair<Integer, Integer>> peakPoints = new ArrayList<Pair<Integer, Integer>>();
		for (int i = 0; i < peaks.size(); i++) {
			FeaturePoint peak = peaks.get(i);
			peakPoints.add(Pair.create(peak.index, peak.amplitude));

//			FeaturePoint lastPeak = i == 0 ? peak : peaks.get(i - 1);
//			DataPoint breath = new DataPoint(highestX + peak.index / ratio,
//					Math.abs(peak.amplitude - lastPeak.amplitude) + 50);
//			longSeries.appendData(breath, true, Config.NUM_SAMPLES_LONG_TERM_GRAPH);
		}
		this.peaks.setData(peakPoints);

		List<Pair<Integer, Integer>> valleyPoints = new ArrayList<Pair<Integer, Integer>>();
		for (int i = 0; i < valleys.size(); i++) {
			FeaturePoint valley = valleys.get(i);
			valleyPoints.add(Pair.create(valley.index, valley.amplitude));
		}
		this.valleys.setData(valleyPoints);
	}
}
