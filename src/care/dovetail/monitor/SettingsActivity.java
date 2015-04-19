package care.dovetail.monitor;

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
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import care.dovetail.monitor.BackgroundService.LocalBinder;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer.GridStyle;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class SettingsActivity extends Activity implements OnSeekBarChangeListener {
	private static final String TAG = "SettingsActivity";

	private App app;
	private BackgroundService service;
	private boolean serviceBound = false;

	private long lastChangeTime = 0;

	private SeekBar threshold1;
	private SeekBar threshold2;
	private GraphView graph;

	LineGraphSeries<DataPoint> audioDataSeries;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		app = (App) getApplication();

		threshold1 = (SeekBar) findViewById(R.id.filter1);
		threshold2 = (SeekBar) findViewById(R.id.filter2);

		graph = ((GraphView) findViewById(R.id.graph));
		audioDataSeries = new LineGraphSeries<DataPoint>();
		graph.addSeries(audioDataSeries);
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
			// Log.i(TAG, Arrays.toString(data));
			final int bpm = intent.getIntExtra(Config.SENSOR_DATA_HEARTBEAT, 0);
			final DataPoint[] dataPoints = new DataPoint[data == null ? 0 : data.length];
			for (int i = 0; i < dataPoints.length; i++) {
				dataPoints[i] = new DataPoint(i, data[i]);
			}
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					audioDataSeries.resetData(dataPoints);
					((TextView) findViewById(R.id.bpm)).setText(Integer.toString(bpm));
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
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}
}
