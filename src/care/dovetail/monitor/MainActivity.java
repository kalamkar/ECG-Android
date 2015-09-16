package care.dovetail.monitor;

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
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import care.dovetail.monitor.BluetoothSmartClient.ConnectionListener;
import care.dovetail.monitor.SignalProcessor.FeaturePoint;
import care.dovetail.monitor.SignalProcessor.FeaturePoint.Type;

public class MainActivity extends Activity implements OnSeekBarChangeListener, ConnectionListener {
	private static final String TAG = "MainActivity";

	@SuppressWarnings("unused")
	private App app;

	private BluetoothLeScanner scanner;
	private BluetoothSmartClient patchClient;

	private SignalProcessor signals;

	private long lastChangeTime = 0;

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

		SeekBar threshold1 = (SeekBar) findViewById(R.id.filter1);
		SeekBar threshold2 = (SeekBar) findViewById(R.id.filter2);

		threshold1.setOnSeekBarChangeListener(this);
		threshold2.setOnSeekBarChangeListener(this);

		signals = new SignalProcessor(threshold1.getProgress(), threshold2.getProgress());

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
			int windowSize = ((SeekBar) findViewById(R.id.filter1)).getProgress();
			int minSlope = ((SeekBar) findViewById(R.id.filter2)).getProgress();
			signals = new SignalProcessor(windowSize, minSlope);

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
	public void onNewValues(final int data[]) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				signals.update(data);
				if (data == null) {
					return;
				}

				List<FeaturePoint> peaks = signals.getFeaturePoints(Type.PEAK);
				List<FeaturePoint> valleys = signals.getFeaturePoints(Type.VALLEY);

				ChartFragment fragment =
						(ChartFragment) getFragmentManager().findFragmentById(R.id.chart);
				fragment.updateGraph(data, peaks, valleys, signals.medianAmplitude);

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
