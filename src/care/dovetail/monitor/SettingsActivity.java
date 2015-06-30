package care.dovetail.monitor;

import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import care.dovetail.monitor.BackgroundService.LocalBinder;
import care.dovetail.monitor.SignalProcessor.FeaturePoint;
import care.dovetail.monitor.SignalProcessor.FeaturePoint.Type;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer.GridStyle;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

public class SettingsActivity extends Activity implements OnSeekBarChangeListener {
	private static final String TAG = "SettingsActivity";

	private App app;
	private BackgroundService service;
	private boolean serviceBound = false;

	private long lastChangeTime = 0;

	private SeekBar threshold1;
	private SeekBar threshold2;
	private GraphView graph;

	LineGraphSeries<DataPoint> audioDataSeries = new LineGraphSeries<DataPoint>();
	PointsGraphSeries<DataPoint> peakDataSeries = new PointsGraphSeries<DataPoint>();
	PointsGraphSeries<DataPoint> valleyDataSeries = new PointsGraphSeries<DataPoint>();
	LineGraphSeries<DataPoint> median = new LineGraphSeries<DataPoint>();

	private SignalProcessor signals;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		app = (App) getApplication();

		signals = new SignalProcessor(app.settings.getAudioThreshold1(),
				app.settings.getAudioThreshold2());

		threshold1 = (SeekBar) findViewById(R.id.filter1);
		threshold2 = (SeekBar) findViewById(R.id.filter2);

		graph = ((GraphView) findViewById(R.id.graph));
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

		graph.getGridLabelRenderer().setGridStyle(GridStyle.NONE);
		graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
			@Override
			public String formatLabel(double value, boolean isValueX) {
				return "";
			}
		});

    	graph.getViewport().setXAxisBoundsManual(true);
		graph.getViewport().setMaxX(512);
		graph.getViewport().setYAxisBoundsManual(true);
		graph.getViewport().setMaxY(256.0);
		graph.getViewport().setMinY(0.0);

		graph.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Log.i(TAG, Arrays.toString(signals.values));
			}
		});

		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
		    Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG).show();
		    ((CheckBox) findViewById(R.id.ble)).setChecked(false);
		} else {
			((CheckBox) findViewById(R.id.ble)).setChecked(true);
		}
	}

	@Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, BackgroundService.class), serviceConnection,
        		Context.BIND_AUTO_CREATE);
        registerReceiver(receiver, new IntentFilter(Config.SERVICE_EVENT));
        registerReceiver(receiver, new IntentFilter(Config.SERVICE_DATA));
    }

    @Override
    protected void onStop() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
            if (service != null) {
        		service.stopRecording();
        	}
        }
        unregisterReceiver(receiver);
        super.onStop();
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent == null) {
				return;
			}
			int data[] = intent.getIntArrayExtra(Config.SENSOR_DATA);
			signals.update(data);
			if (data == null) {
				return;
			}
			final DataPoint[] medianPoints = new DataPoint[2];
			medianPoints[0] = new DataPoint(0, signals.medianAmplitude);
			medianPoints[1] = new DataPoint(data.length, signals.medianAmplitude);

			final DataPoint[] dataPoints = new DataPoint[data.length];
			for (int i = 0; i < dataPoints.length; i++) {
				dataPoints[i] = new DataPoint(i, data[i]);
			}
			List<FeaturePoint> peaks = signals.getFeaturePoints(Type.PEAK);
			final DataPoint[] peakPoints = new DataPoint[peaks.size()];
			for (int i = 0; i < peakPoints.length; i++) {
				FeaturePoint peak = peaks.get(i);
				peakPoints[i] = new DataPoint(peak.index, peak.amplitude);
			}
			List<FeaturePoint> valleys = signals.getFeaturePoints(Type.VALLEY);
			final DataPoint[] valleyPoints = new DataPoint[valleys.size()];
			for (int i = 0; i < valleyPoints.length; i++) {
				FeaturePoint valley = valleys.get(i);
				valleyPoints[i] = new DataPoint(valley.index, valley.amplitude);
			}
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					audioDataSeries.resetData(dataPoints);
					peakDataSeries.resetData(peakPoints);
					valleyDataSeries.resetData(valleyPoints);
					median.resetData(medianPoints);

					int peakCount = signals.count.get(Type.PEAK);
					int valleyCount = signals.count.get(Type.VALLEY);
					int bpm = peakCount > valleyCount ? signals.bpm.get(Type.PEAK)
							: signals.bpm.get(Type.VALLEY);
					((TextView) findViewById(R.id.bpm)).setText(Integer.toString(bpm));
					((TextView) findViewById(R.id.peaks)).setText(Integer.toString(peakCount));
					((TextView) findViewById(R.id.valleys)).setText(Integer.toString(valleyCount));
					int amp = peakCount > valleyCount
							? signals.avgAmp.get(Type.PEAK) : signals.avgAmp.get(Type.VALLEY);
					((TextView) findViewById(R.id.amp)).setText(Integer.toString(amp));
					((TextView) findViewById(R.id.mean)).setText(
							Integer.toString(signals.medianAmplitude));
				}
			});
		}
    };

	private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            service = ((LocalBinder) binder).getService();
            if (service == null) {
            	return;
            }
            serviceBound = true;
            service.startRecording();
        	updateUi();
        	setUiChangeListeners();
        }

        @Override
        public void onServiceDisconnected(ComponentName component) {
        	service = null;
        	serviceBound = false;
        }
    };

    private void setUiChangeListeners() {
		threshold1.setOnSeekBarChangeListener(this);
		threshold2.setOnSeekBarChangeListener(this);
    }

    private void updateUi() {
    	if (service == null) {
    		return;
    	}

    	threshold1.setProgress(app.settings.getAudioThreshold1());
    	threshold2.setProgress(app.settings.getAudioThreshold2());

    	((TextView) findViewById(R.id.filter1Text)).setText(
    			Integer.toString(app.settings.getAudioThreshold1()));
    	((TextView) findViewById(R.id.filter2Text)).setText(
    			Integer.toString(app.settings.getAudioThreshold2()));
		signals = new SignalProcessor(app.settings.getAudioThreshold1(),
				app.settings.getAudioThreshold2());
    }

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		long currentTime = System.currentTimeMillis();
		if (fromUser && currentTime - lastChangeTime > Config.UI_UPDATE_INTERVAL_MILLIS) {
			if (seekBar.getId() == R.id.filter1) {
				app.settings.setAudioThreshold1(progress);
				((TextView) findViewById(R.id.filter1Text)).setText(Integer.toString(progress));
			} else if (seekBar.getId() == R.id.filter2) {
				app.settings.setAudioThreshold2(progress);
				((TextView) findViewById(R.id.filter2Text)).setText(Integer.toString(progress));
			}
			signals = new SignalProcessor(app.settings.getAudioThreshold1(),
					app.settings.getAudioThreshold2());
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}
}
