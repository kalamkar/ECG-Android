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
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import biz.source_code.dsp.filter.FilterCharacteristicsType;
import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.IirFilterDesignFisher;
import care.dovetail.monitor.BluetoothSmartClient.ConnectionListener;
import care.dovetail.monitor.NewSignalProcessor.FeaturePoint;

public class MainActivity extends Activity implements ConnectionListener, OnClickListener {
	private static final String TAG = "MainActivity";

	private static final String BTLE_ADDRESS = "BTLE_ADDRESS";

	private App app;

	private BluetoothLeScanner scanner;
	private BluetoothSmartClient patchClient;

	private IirFilter filter;
	private final NewSignalProcessor signals = new NewSignalProcessor();

	private long lastUpdateTime = System.currentTimeMillis();

	private boolean connected = false;
	private boolean paused = false;
	private EcgDataWriter writer = null;

	private final int data[] = new int[Config.GRAPH_LENGTH];

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
		}

		findViewById(R.id.record).setOnClickListener(this);
		findViewById(R.id.freeze).setOnClickListener(this);
		findViewById(R.id.mute).setOnClickListener(this);
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
    		// patchClient.disableNotifications();
    		patchClient.disconnect();
    		patchClient = null;
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
	public void onClick(View view) {
		if (!connected) {
			return;
		}
		switch(view.getId()) {
		case R.id.record:
			if (writer == null) {
				writer = new EcgDataWriter(app);
				((ImageView) view).setImageResource(R.drawable.ic_action_stop);
			} else {
				writer.close();
				writer = null;
				((ImageView) view).setImageResource(R.drawable.ic_action_upload);
			}
			break;
		case R.id.freeze:
			if (paused) {
				paused = false;
				((ImageView) view).setImageResource(R.drawable.ic_action_pause);
			} else {
				paused = true;
				((ImageView) view).setImageResource(R.drawable.ic_action_play);
				if (writer != null) {
					writer.close();
					writer = null;
				}
			}
			break;
		case R.id.mute:
			if (player == null) {
				((ImageView) view).setImageResource(R.drawable.ic_action_mute);
				player = new  AudioTrack(AudioManager.STREAM_MUSIC,
						Config.AUDIO_PLAYBACK_RATE,
						AudioFormat.CHANNEL_OUT_MONO,
						AudioFormat.ENCODING_PCM_8BIT,
						Config.GRAPH_LENGTH * Config.AUDIO_BYTES_PER_SAMPLE,
						AudioTrack.MODE_STREAM);
				player.play();
			} else {
				((ImageView) view).setImageResource(R.drawable.ic_action_audio);
				player.release();
				player = null;
			}
			break;
		}
	}

	private void startScan() {
		Log.i(TAG, "Starting scan for BTLE patch.");
		ScanSettings settings = new ScanSettings.Builder()
				.setScanMode(ScanSettings.SCAN_MODE_BALANCED).setReportDelay(0).build();
		scanner.startScan(null, settings, callback);
		findViewById(R.id.progress).setVisibility(View.VISIBLE);
		findViewById(R.id.status).setVisibility(View.INVISIBLE);
		((TextView) findViewById(R.id.label_status)).setText(R.string.connecting);
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
		findViewById(R.id.status).setVisibility(View.VISIBLE);
	}

	@Override
	public void onConnect(String address) {
		connected = true;
		Log.i(TAG, String.format("Connected to %s", address));
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				((ImageView) findViewById(R.id.status)).setImageResource(R.drawable.ic_connected);
				((TextView) findViewById(R.id.label_status)).setText(R.string.connected);
			}
		});
	}

	@Override
	public void onDisconnect(String address) {
		connected = false;
		Log.i(TAG, String.format("Disconnected from %s", address));
		patchClient = null;
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				((ImageView) findViewById(R.id.status)).setImageResource(R.drawable.ic_warning);
				((TextView) findViewById(R.id.label_status)).setText(R.string.disconnected);
			}
		});
	}

	@Override
	public void onServiceDiscovered(boolean success) {
		if (success && patchClient != null) {
			Log.i(TAG, "Notification service discoverd, enabling notifications");
			patchClient.enableNotifications();
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
			if (player != null) {
				playAudio();
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

				List<FeaturePoint> peaks = signals.getFeaturePoints(FeaturePoint.Type.QRS);

				fragment.clear();
				fragment.updateGraph(data);
				// fragment.updateLongGraph(peaks);
				fragment.updateMarkers(peaks, signals.medianAmplitude);

				((TextView) findViewById(R.id.bpm)).setText(Integer.toString(signals.bpm));
			}
		});
	}

	private static byte[] getBytes(List<FeaturePoint> points) {
		byte bytes[] = new byte[Config.GRAPH_LENGTH * Config.AUDIO_BYTES_PER_SAMPLE];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) 128;
		}
		for (int i = 0; i < points.size(); i++) {
			for (int j = 0; j < Config.AUDIO_BYTES_PER_SAMPLE; j++) {
				int value = (int) (128 - Math.sin( Math.toRadians(30 / (j + 1)) ) * 255);
				bytes[points.get(i).index * Config.AUDIO_BYTES_PER_SAMPLE + j] = (byte) value;
			}
		}
		return bytes;
	}

	private void playAudio() {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				try {
					byte audio[] = getBytes(signals.getFeaturePoints(FeaturePoint.Type.QRS));
					player.write(audio, 0, audio.length);
				} catch (Throwable t) {
					Log.e(TAG, t.getCause() != null
							? t.getCause().getMessage() : t.getMessage(), t);
				}
				return null;
			}
		}.execute();
	}
}
