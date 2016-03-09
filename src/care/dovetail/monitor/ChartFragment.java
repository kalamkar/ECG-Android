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
		ecg.setXRange(0, Config.GRAPH_LENGTH);
		ecg.setYRange(Config.SHORT_GRAPH_MIN, Config.SHORT_GRAPH_MAX);

		peaks = ((ChartView) getView().findViewById(R.id.peaks));
		peaks.setType(Type.POINT);
		peaks.setThickness(5);
		peaks.setColor(getResources().getColor(android.R.color.holo_orange_dark));
		peaks.setXRange(0, Config.GRAPH_LENGTH);
		peaks.setYRange(Config.SHORT_GRAPH_MIN, Config.SHORT_GRAPH_MAX);

		valleys = ((ChartView) getView().findViewById(R.id.valleys));
		valleys.setType(Type.POINT);
		valleys.setThickness(5);
		valleys.setColor(getResources().getColor(android.R.color.holo_blue_dark));
		valleys.setXRange(0, Config.GRAPH_LENGTH);
		valleys.setYRange(Config.SHORT_GRAPH_MIN, Config.SHORT_GRAPH_MAX);

		median = ((ChartView) getView().findViewById(R.id.median));
		median.setColor(getResources().getColor(android.R.color.darker_gray));
		median.setXRange(0, Config.GRAPH_LENGTH);
		median.setYRange(Config.SHORT_GRAPH_MIN, Config.SHORT_GRAPH_MAX);

		breath = ((ChartView) getView().findViewById(R.id.breath));
		breath.setColor(getResources().getColor(android.R.color.holo_green_light));
		breath.setXRange(0, Config.LONG_TERM_GRAPH_LENGTH);
		breath.setYRange(Config.LONG_GRAPH_MIN, Config.LONG_GRAPH_MAX);
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

		List<Pair<Integer, Integer>> peakPoints = new ArrayList<Pair<Integer, Integer>>();
		for (int i = 0; i < peaks.size(); i++) {
			FeaturePoint peak = peaks.get(i);
			peakPoints.add(Pair.create(peak.index, peak.amplitude));
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
