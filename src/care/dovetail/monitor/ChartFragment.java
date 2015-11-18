package care.dovetail.monitor;

import java.util.ArrayList;
import java.util.List;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import care.dovetail.monitor.SignalProcessor.FeaturePoint;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer.GridStyle;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

public class ChartFragment extends Fragment {
	private static final String TAG = "ChartFragment";

	private LineGraphSeries<DataPoint> dataSeries = new LineGraphSeries<DataPoint>();
	private PointsGraphSeries<DataPoint> peakDataSeries = new PointsGraphSeries<DataPoint>();
	private PointsGraphSeries<DataPoint> valleyDataSeries = new PointsGraphSeries<DataPoint>();
	private LineGraphSeries<DataPoint> median = new LineGraphSeries<DataPoint>();
	// private LineGraphSeries<DataPoint> longSeries = new LineGraphSeries<DataPoint>();

	private final List<Integer> cache = new ArrayList<Integer>(Config.NUM_SAMPLES_AVERAGE);

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_chart, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		GraphView graph = ((GraphView) view.findViewById(R.id.graph));
		graph.addSeries(dataSeries);

		dataSeries.setColor(0xFF0000FF);
		dataSeries.setThickness(1);

		graph.addSeries(peakDataSeries);
		peakDataSeries.setSize(10);
		peakDataSeries.setColor(getResources().getColor(android.R.color.holo_orange_dark));

		graph.addSeries(valleyDataSeries);
		valleyDataSeries.setSize(10);
		valleyDataSeries.setColor(getResources().getColor(android.R.color.holo_blue_dark));

		graph.addSeries(median);
		median.setThickness(2);
		median.setColor(getResources().getColor(android.R.color.darker_gray));

//		GraphView longGraph = ((GraphView) view.findViewById(R.id.longGraph));
//		longGraph.addSeries(longSeries);
//		longSeries.setThickness(2);
//		longSeries.setColor(getResources().getColor(android.R.color.holo_green_light));

		initializeGraph(graph, Config.DATA_LENGTH);
//		initializeGraph(longGraph, Config.NUM_SAMPLES_LONG_TERM_GRAPH);
	}

	private void initializeGraph(GraphView graph, double maxX) {
		graph.getGridLabelRenderer().setGridStyle(GridStyle.NONE);
		graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
			@Override
			public String formatLabel(double value, boolean isValueX) {
				return "";
			}
		});

    	graph.getViewport().setXAxisBoundsManual(true);
		graph.getViewport().setMaxX(maxX);
		graph.getViewport().setYAxisBoundsManual(true);
		graph.getViewport().setMaxY(256.0);
		graph.getViewport().setMinY(0.0);
	}

	public void updateGraph(int data[]) {
//		double highestX = longSeries.getHighestValueX();
		DataPoint[] dataPoints = new DataPoint[data.length];
		for (int i = 0; i < dataPoints.length; i++) {
			dataPoints[i] = new DataPoint(i, data[i]);
//				cache.add(data[i]);
//				if (cache.size() == Config.NUM_SAMPLES_AVERAGE) {
//					longSeries.appendData(new DataPoint(highestX + i / Config.NUM_SAMPLES_AVERAGE,
//							average(cache.toArray(new Integer[0]))), true,
//							Config.NUM_SAMPLES_LONG_TERM_GRAPH);
//					cache.clear();
//				}
		}
		dataSeries.resetData(dataPoints);
	}

	public void updateMarkers(List<FeaturePoint> peaks, List<FeaturePoint> valleys,
			int medianAmplitude) {
		median.resetData(new DataPoint[] { new DataPoint(0, medianAmplitude),
				new DataPoint(Config.DATA_LENGTH, medianAmplitude) });

		DataPoint[] peakPoints = new DataPoint[peaks.size()];
		for (int i = 0; i < peakPoints.length; i++) {
			FeaturePoint peak = peaks.get(i);
			peakPoints[i] = new DataPoint(peak.index, peak.amplitude);
		}

		DataPoint[] valleyPoints = new DataPoint[valleys.size()];
		for (int i = 0; i < valleyPoints.length; i++) {
			FeaturePoint valley = valleys.get(i);
			valleyPoints[i] = new DataPoint(valley.index, valley.amplitude);
		}

		peakDataSeries.resetData(peakPoints);
		valleyDataSeries.resetData(valleyPoints);
	}

	private static int average(Integer values[]) {
		int sum = 0;
		for (int value : values) {
			sum += value;
		}
		return sum / values.length;
	}
}
