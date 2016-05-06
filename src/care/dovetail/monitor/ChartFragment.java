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
import care.dovetail.monitor.ChartView.Chart;
import care.dovetail.monitor.SignalProcessor.Feature;

public class ChartFragment extends Fragment {
	private static final String TAG = "ChartFragment";

	private Chart ecg;
	private Chart peaks;
	private Chart median;
	private Chart breath;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_chart, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		ChartView ecgView = ((ChartView) getView().findViewById(R.id.ecg));
		ecg = ecgView.makeLineChart(Color.BLUE, 1);
		ecg.setXRange(0, Config.GRAPH_LENGTH);
		ecg.setYRange(Config.SHORT_GRAPH_MIN, Config.SHORT_GRAPH_MAX);

		peaks =	ecgView.makePointsChart(
					getResources().getColor(android.R.color.holo_orange_dark), 3);
		peaks.setXRange(0, Config.GRAPH_LENGTH);
		peaks.setYRange(Config.SHORT_GRAPH_MIN, Config.SHORT_GRAPH_MAX);

		median = ecgView.makeLineChart(getResources().getColor(android.R.color.darker_gray), 2);
		median.setXRange(0, Config.GRAPH_LENGTH);
		median.setYRange(Config.SHORT_GRAPH_MIN, Config.SHORT_GRAPH_MAX);

		ChartView breathView = ((ChartView) getView().findViewById(R.id.breath));
		breath = breathView.makePointsChart(
					getResources().getColor(android.R.color.holo_green_light), 1);
		breath.setXRange(0, Config.GRAPH_LENGTH);
		breath.setYRange(Config.LONG_GRAPH_MIN, Config.LONG_GRAPH_MAX);
	}

	public void clear() {
		((ChartView) getView().findViewById(R.id.ecg)).clear();
		((ChartView) getView().findViewById(R.id.breath)).clear();
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

	public void updateMarkers(List<Feature> peaks, int medianAmplitude) {
		List<Pair<Integer, Integer>> medianPoints = new ArrayList<Pair<Integer, Integer>>(2);
		medianPoints.add(Pair.create(0, medianAmplitude));
		medianPoints.add(Pair.create(Config.GRAPH_LENGTH - 1, medianAmplitude));
		median.setData(medianPoints);

		List<Pair<Integer, Integer>> peakPoints = new ArrayList<Pair<Integer, Integer>>();
		for (int i = 0; i < peaks.size(); i++) {
			Feature peak = peaks.get(i);
			peakPoints.add(Pair.create(peak.index, peak.min));
			peakPoints.add(Pair.create(peak.index, peak.max));
		}
		this.peaks.setData(peakPoints);
	}
}
