package care.dovetail.monitor;

import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
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

	public static final String WINDOW_SIZE = "WINDOW_SIZE";
	public static final String MIN_SLOPE = "MIN_SLOPE";

	private App app;

	private LineGraphSeries<DataPoint> audioDataSeries = new LineGraphSeries<DataPoint>();
	private PointsGraphSeries<DataPoint> peakDataSeries = new PointsGraphSeries<DataPoint>();
	private PointsGraphSeries<DataPoint> valleyDataSeries = new PointsGraphSeries<DataPoint>();
	private LineGraphSeries<DataPoint> median = new LineGraphSeries<DataPoint>();
	private LineGraphSeries<DataPoint> longSeries = new LineGraphSeries<DataPoint>();

	private LimitedQueue<DataPoint> longData =
			new LimitedQueue<DataPoint>(Config.NUM_SAMPLES_LONG_TERM_GRAPH);

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		app = (App) activity.getApplication();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_chart, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		GraphView graph = ((GraphView) view.findViewById(R.id.graph));
		graph.addSeries(audioDataSeries);

		graph.addSeries(peakDataSeries);
		peakDataSeries.setSize(10);
		peakDataSeries.setColor(getResources().getColor(android.R.color.holo_orange_dark));

		graph.addSeries(valleyDataSeries);
		valleyDataSeries.setSize(10);
		valleyDataSeries.setColor(getResources().getColor(android.R.color.holo_blue_dark));

		graph.addSeries(median);
		median.setThickness(2);
		median.setColor(getResources().getColor(android.R.color.darker_gray));

		GraphView longGraph = ((GraphView) view.findViewById(R.id.longGraph));
		longGraph.addSeries(longSeries);
		longSeries.setThickness(2);
		longSeries.setColor(getResources().getColor(android.R.color.holo_green_light));

		initializeGraph(graph, 512);
		initializeGraph(longGraph, Config.NUM_SAMPLES_LONG_TERM_GRAPH);
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

	public void updateGraph(int data[], List<FeaturePoint> peaks, List<FeaturePoint> valleys,
			int medianAmplitude) {
		DataPoint[] medianPoints = new DataPoint[2];
		medianPoints[0] = new DataPoint(0, medianAmplitude);
		medianPoints[1] = new DataPoint(data.length, medianAmplitude);

		double highestX = longSeries.getHighestValueX();
		DataPoint[] dataPoints = new DataPoint[data.length];
		for (int i = 0; i < dataPoints.length; i++) {
			dataPoints[i] = new DataPoint(i, data[i]);
			longData.add(new DataPoint(highestX + i, data[i]));
		}

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

		audioDataSeries.resetData(dataPoints);
		peakDataSeries.resetData(peakPoints);
		valleyDataSeries.resetData(valleyPoints);
		median.resetData(medianPoints);
		longSeries.resetData(longData.toArray(new DataPoint[0]));
	}

	private static class LimitedQueue<E> extends LinkedList<E> {
	    private int limit;

	    public LimitedQueue(int limit) {
	        this.limit = limit;
	    }

	    @Override
	    public boolean add(E o) {
	        super.add(o);
	        while (size() > limit) { super.remove(); }
	        return true;
	    }
	}
}
