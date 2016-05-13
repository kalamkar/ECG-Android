package care.dovetail.monitor.ui;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import care.dovetail.monitor.Config;
import care.dovetail.monitor.DemoActivity;
import care.dovetail.monitor.R;
import care.dovetail.monitor.SignalProcessor.Feature;
import care.dovetail.monitor.ui.ChartView.Chart;

public class EcgFragment extends Fragment {
	private static final String TAG = "ChartFragment";

	private Chart ecg;
	private Chart peaks;
	private Chart median;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_chart, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		if (!getActivity().getIntent().hasExtra(DemoActivity.DEMO_FLAG)) {
			view.findViewById(R.id.grid).setVisibility(View.INVISIBLE);
		}

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
	}

	public void clear() {
		((ChartView) getView().findViewById(R.id.ecg)).clear();
		((ChartView) getView().findViewById(R.id.breath)).clear();
	}

	public void update(int data[], List<Feature> peaks, int medianAmplitude) {
		List<Pair<Integer, Integer>> points = new ArrayList<Pair<Integer, Integer>>(data.length);
		for (int i = 0; i < data.length; i++) {
			points.add(Pair.create(i, data[i]));
		}
		ecg.setData(points);

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

		getView().findViewById(R.id.ecg).invalidate();
	}
}
