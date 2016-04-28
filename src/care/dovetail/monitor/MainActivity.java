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
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import biz.source_code.dsp.filter.FilterCharacteristicsType;
import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.IirFilterDesignFisher;
import care.dovetail.monitor.BluetoothSmartClient.ConnectionListener;
import care.dovetail.monitor.SignalProcessor.FeaturePoint;
import care.dovetail.monitor.SignalProcessor.FeaturePoint.Type;

public class MainActivity extends Activity implements ConnectionListener {
	private static final String TAG = "MainActivity";

	private static final String BTLE_ADDRESS = "BTLE_ADDRESS";

	@SuppressWarnings("unused")
	private App app;

	private BluetoothLeScanner scanner;
	private BluetoothSmartClient patchClient;

	private IirFilter filter;
	private final SignalProcessor signals = new SignalProcessor();

	private long lastChangeTime = 0;
	private long lastUpdateTime = System.currentTimeMillis();

	private Timer staleTimer;

	private EcgDataWriter writer = null;
	private boolean paused = false;

	private final int data[] = new int[Config.GRAPH_LENGTH];
	private final int longData[] = new int[Config.LONG_TERM_GRAPH_LENGTH];

	private int updateCount = 0;
	private int audioBufferLength = 0;

	private AudioTrack player;

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

		filter = new IirFilter(IirFilterDesignFisher.design(
				FilterPassType.lowpass, FilterCharacteristicsType.bessel, 4, 0, 0.1, 0));

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
//		player = new  AudioTrack(AudioManager.STREAM_MUSIC,
//									4 * 1000 / Config.SAMPLE_INTERVAL_MS,
//									AudioFormat.CHANNEL_OUT_MONO,
//									AudioFormat.ENCODING_PCM_8BIT,
//									Config.GRAPH_LENGTH * 4,
//									AudioTrack.MODE_STREAM);
//		player.play();
    }

    @Override
    protected void onStop() {
    	stopScan();
    	if (patchClient != null) {
    		// patchClient.disableNotifications();
    		patchClient.disconnect();
    		patchClient = null;
    	}
    	if (staleTimer != null) {
    		staleTimer.cancel();
    	}
		if (writer != null) {
			writer.close();
			writer = null;
		}
		if (player != null) {
			player.release();
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
					writer = new EcgDataWriter(app);
					item.setTitle(getResources().getString(R.string.action_stop));
				} else {
					writer.close();
					writer = null;
					item.setTitle(getResources().getString(R.string.action_record));
				}
			}
			break;
		case R.id.action_pause:
			if (paused) {
				paused = false;
				item.setIcon(getResources().getDrawable(android.R.drawable.ic_media_pause));
			} else {
				paused = true;
				item.setIcon(getResources().getDrawable(android.R.drawable.ic_media_play));
				if (writer != null) {
					writer.close();
					writer = null;
					item.setTitle(getResources().getString(R.string.action_record));
				}
			}
			break;
		}
		return super.onOptionsItemSelected(item);
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
			if (name != null && name.startsWith(Config.BT_DEVICE_NAME_PREFIX)) {
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
	public void onNewValues(int chunk[]) {
		updateCount++;
		audioBufferLength += chunk.length;
		if (writer != null) {
			writer.write(chunk);
		}

		System.arraycopy(data, chunk.length, data, 0, data.length - chunk.length);
		System.arraycopy(chunk, 0, data, data.length - chunk.length, chunk.length);
		updateLongData(signals.medianAmplitude);
		if (updateCount < Config.GRAPH_UPDATE_COUNT) {
			return;
		}
		updateCount = 0;
		lastUpdateTime = System.currentTimeMillis();

		if (paused) {
			return;
		}

		signals.update(data);
		if (audioBufferLength == Config.GRAPH_LENGTH) {
			audioBufferLength = 0;
			try {
				byte audio[] = getBytes(signals.getFeaturePoints(Type.PEAK));
	            player.write(audio, 0, audio.length);
	        } catch (Throwable t) {
	        	Log.e(TAG, t.getCause() != null ? t.getCause().getMessage() : t.getMessage(), t);
	        }
		}

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ChartFragment fragment =
						(ChartFragment) getFragmentManager().findFragmentById(R.id.chart);
				if (fragment == null) {
					return;
				}

				List<FeaturePoint> peaks = signals.getFeaturePoints(Type.PEAK);
				List<FeaturePoint> valleys = signals.getFeaturePoints(Type.VALLEY);

				fragment.clear();
				fragment.updateGraph(data);
				fragment.updateLongGraph(longData);
				fragment.updateMarkers(peaks, valleys, signals.medianAmplitude);

				int bpm = signals.bpm.get(Type.PEAK);
				((TextView) findViewById(R.id.bpm)).setText(Integer.toString(bpm));
			}
		});
	}

	private void updateLongData(int lastValue) {
		System.arraycopy(longData, 1, longData, 0, longData.length - 1);
		longData[longData.length - 1] = lastValue;
	}

	private static byte[] getBytes(List<FeaturePoint> points) {
		byte bytes[] = new byte[Config.GRAPH_LENGTH * 4];
		for (int i = 0; i < points.size(); i++) {
			bytes[points.get(i).index * 4] = (byte) 200; // (byte) points.get(i).amplitude;
			bytes[points.get(i).index * 4 + 1] = (byte) 255; // (byte) points.get(i).amplitude;
			bytes[points.get(i).index * 4 + 2] = (byte) 255; // (byte) points.get(i).amplitude;
			bytes[points.get(i).index * 4 + 3] = (byte) 200; // (byte) points.get(i).amplitude;
		}
		for (int i = 0; i < bytes.length; i++) {
			if (bytes[i] == 0) {
				bytes[i] = (byte) 128;
			}
		}
		return bytes;
	}
}
