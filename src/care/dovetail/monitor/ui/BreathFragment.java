package care.dovetail.monitor.ui;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import care.dovetail.monitor.Config;
import care.dovetail.monitor.R;
import care.dovetail.monitor.R.id;
import care.dovetail.monitor.R.layout;
import care.dovetail.monitor.ui.ChartView.Chart;

public class BreathFragment extends Fragment {
	private static final String TAG = "BreathGraphFragment";

	private Chart breath;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_breath, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		ChartView breathView = ((ChartView) getView().findViewById(R.id.breath));
		breath = breathView.makePointsChart(
					getResources().getColor(android.R.color.holo_green_light), 3);
		breath.setXRange(0, Config.BREATH_GRAPH_LENGTH);
		breath.setYRange(Config.LONG_GRAPH_MIN, Config.LONG_GRAPH_MAX);
	}

	public void clear() {
		((ChartView) getView().findViewById(R.id.breath)).clear();
	}

	public void updateGraph(int data[]) {
		List<Pair<Integer, Integer>> points = new ArrayList<Pair<Integer, Integer>>(data.length);
		for (int i = 0; i < data.length; i++) {
			points.add(Pair.create(i, data[i]));
		}
		breath.setData(points);
	}
}
