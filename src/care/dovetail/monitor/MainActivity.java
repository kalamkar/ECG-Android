package care.dovetail.monitor;

import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import care.dovetail.monitor.BluetoothSmartClient.ConnectionListener;
import care.dovetail.monitor.SignalProcessor.FeaturePoint;
import care.dovetail.monitor.SignalProcessor.FeaturePoint.Type;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer.GridStyle;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

public class MainActivity extends Activity implements OnSeekBarChangeListener, ConnectionListener {
	private static final String TAG = "MainActivity";

	private App app;

	private BluetoothLeScanner scanner;
	private BluetoothSmartClient patchClient;

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
		setContentView(R.layout.activity_main);

		app = (App) getApplication();

		final BluetoothManager bluetoothManager =
				(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetooth = bluetoothManager.getAdapter();
		scanner = bluetooth.getBluetoothLeScanner();

		if (bluetooth == null || !bluetooth.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, 0);
		}

		threshold1 = (SeekBar) findViewById(R.id.filter1);
		threshold2 = (SeekBar) findViewById(R.id.filter2);

		threshold1.setOnSeekBarChangeListener(this);
		threshold2.setOnSeekBarChangeListener(this);

		signals = new SignalProcessor(threshold1.getProgress(), threshold2.getProgress());

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
        startScan();
    }

    @Override
    protected void onStop() {
    	stopScan();
    	if (patchClient != null) {
    		patchClient.disableNotifications();
    		patchClient.disconnect();
    	}
        super.onStop();
    }

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		long currentTime = System.currentTimeMillis();
		if (fromUser && currentTime - lastChangeTime > Config.UI_UPDATE_INTERVAL_MILLIS) {
			if (seekBar.getId() == R.id.filter1) {
				((TextView) findViewById(R.id.filter1Text)).setText(Integer.toString(progress));
			} else if (seekBar.getId() == R.id.filter2) {
				((TextView) findViewById(R.id.filter2Text)).setText(Integer.toString(progress));
			}
			signals = new SignalProcessor(threshold1.getProgress(), threshold2.getProgress());
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}

	private void startScan() {
		Log.i(TAG, "Starting scan for BTLE patch.");
		ScanSettings settings = new ScanSettings.Builder()
				.setScanMode(ScanSettings.SCAN_MODE_BALANCED).setReportDelay(0).build();
		scanner.startScan(null, settings, callback);
		findViewById(R.id.progress).setVisibility(View.VISIBLE);
	}

	private ScanCallback callback = new ScanCallback() {
		@Override
		public void onScanFailed(int errorCode) {
			Toast.makeText(MainActivity.this,
					String.format("Bluetooth LE scan failed with error %d", errorCode),
					Toast.LENGTH_LONG).show();
			super.onScanFailed(errorCode);
		}

		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			String name = result.getDevice().getName();
			name = name == null ? result.getScanRecord().getDeviceName() : name;
			if (Config.BT_DEVICE_NAME.equalsIgnoreCase(name)) {
				stopScan();
				Log.i(TAG, String.format("Found device %s", name));
				patchClient = new BluetoothSmartClient(MainActivity.this, MainActivity.this,
						result.getDevice().getAddress());
			}
			super.onScanResult(callbackType, result);
		}
	};

	private void stopScan() {
		Log.i(TAG, "Stopping scan for BTLE patch.");
		scanner.stopScan(callback);
		findViewById(R.id.progress).setVisibility(View.INVISIBLE);
	}

	@Override
	public void onConnect(String address) {
		Log.i(TAG, String.format("Connected to %s", address));
	}

	@Override
	public void onDisconnect(String address) {
		Log.i(TAG, String.format("Disconnected from %s", address));
		patchClient = null;
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				startScan();
			}
		});
	}

	@Override
	public void onServiceDiscovered(boolean success) {
		if (success) {
			Log.i(TAG, "Notification service discoverd, enabling notifications");
			patchClient.enableNotifications();
		} else {
			Log.e(TAG, "Notification service not found.");
		}
	}

	@Override
	public void onNewValues(int data[]) {
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
				int bpm = signals.bpm.get(Type.PEAK);
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
}
