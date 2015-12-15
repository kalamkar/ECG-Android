package care.dovetail.monitor;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import biz.source_code.dsp.filter.FilterCharacteristicsType;
import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.IirFilterDesignFisher;
import care.dovetail.monitor.BluetoothSmartClient.ConnectionListener;
import care.dovetail.monitor.SignalProcessor.FeaturePoint;
import care.dovetail.monitor.SignalProcessor.FeaturePoint.Type;

public class MainActivity extends Activity implements OnSeekBarChangeListener, ConnectionListener {
	private static final String TAG = "MainActivity";

	private static final String BTLE_ADDRESS = "BTLE_ADDRESS";

	@SuppressWarnings("unused")
	private App app;

	private BluetoothLeScanner scanner;
	private BluetoothSmartClient patchClient;

	IirFilter filter;
	private SignalProcessor signals;

	private long lastChangeTime = 0;
	private long lastUpdateTime = System.currentTimeMillis();

	private Timer staleTimer;

	private EcgDataWriter writer = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		app = (App) getApplication();

		BluetoothManager bluetoothManager =
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

		filter = new IirFilter(IirFilterDesignFisher.design(
				FilterPassType.lowpass, FilterCharacteristicsType.bessel, 4, 0, 0.1, 0));
		signals = new SignalProcessor(threshold1.getProgress(), threshold2.getProgress());

		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
		    Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG).show();
		    ((CheckBox) findViewById(R.id.ble)).setChecked(false);
		} else {
			((CheckBox) findViewById(R.id.ble)).setChecked(true);
		}
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		String address = savedInstanceState.getString(BTLE_ADDRESS, null);
		if (address != null) {
			patchClient = new BluetoothSmartClient(this, this, address);
		}
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString(BTLE_ADDRESS, patchClient != null ? patchClient.getDevice() : null);
		super.onSaveInstanceState(outState);
	}

	@Override
    protected void onStart() {
        super.onStart();
        startScan();
    }

    @Override
    protected void onStop() {
    	stopScan();
    	if (patchClient != null) {
    		patchClient.disableNotifications();
    		patchClient.disconnect();
    	}
    	if (staleTimer != null) {
    		staleTimer.cancel();
    	}
        super.onStop();
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.action_record:
			if (((CheckBox) findViewById(R.id.connected)).isChecked()) {
				if (writer == null) {
					writer = new EcgDataWriter(this);
					item.setTitle(getResources().getString(R.string.action_stop));
				} else {
					writer.close();
					writer = null;
					item.setTitle(getResources().getString(R.string.action_record));
				}
			}
			break;
		}
		return super.onOptionsItemSelected(item);
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
		if (scanner != null) {
			scanner.stopScan(callback);
		}
		findViewById(R.id.progress).setVisibility(View.INVISIBLE);
	}

	@Override
	public void onConnect(String address) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				((CheckBox) findViewById(R.id.connected)).setChecked(true);
			}
		});
		Log.i(TAG, String.format("Connected to %s", address));
		staleTimer = new Timer();
		staleTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						long seconds = (System.currentTimeMillis() - lastUpdateTime) / 1000;
						TextView secondsView = (TextView) findViewById(R.id.seconds);
						if (secondsView != null) {
							secondsView.setText(Long.toString(seconds));
						}
					}
				});
			}
		}, 0, 1000);
	}

	@Override
	public void onDisconnect(String address) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				((CheckBox) findViewById(R.id.connected)).setChecked(false);
			}
		});
		staleTimer.cancel();
		staleTimer = null;
		Log.i(TAG, String.format("Disconnected from %s", address));
		patchClient = null;
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
		lastUpdateTime = System.currentTimeMillis();
		if (writer != null) {
			writer.write(data);
		}

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ChartFragment fragment =
						(ChartFragment) getFragmentManager().findFragmentById(R.id.chart);
				if (fragment == null || data == null) {
					return;
				}

//				for (int i = 0; i < data.length; i++) {
//					data[i] = (int) filter.step(data[i]);
//				}

				signals.update(data);
				List<FeaturePoint> peaks = signals.getFeaturePoints(Type.PEAK);
				List<FeaturePoint> valleys = signals.getFeaturePoints(Type.VALLEY);

				fragment.updateGraph(data);
				fragment.updateMarkers(peaks, valleys, signals.medianAmplitude);

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
