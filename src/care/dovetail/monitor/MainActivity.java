package care.dovetail.monitor;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import biz.source_code.dsp.filter.FilterCharacteristicsType;
import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.IirFilterDesignFisher;
import care.dovetail.monitor.BluetoothSmartClient.BluetoothDeviceListener;
import care.dovetail.monitor.SignalProcessor.Feature;

public class MainActivity extends Activity implements BluetoothDeviceListener, OnClickListener {
	private static final String TAG = "MainActivity";

	private App app;

	private BluetoothSmartClient patchClient;

	private IirFilter filter;
	private final SignalProcessor signals = new SignalProcessor();

	private boolean connected = false;
	private boolean paused = false;
	private EcgDataWriter writer = null;

	private int audioBufferLength = 0;
	private AudioPlayer player;

	private Timer chartUpdateTimer = null;
	private Timer bpmUpdateTimer = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate");
		super.onCreate(savedInstanceState);

		if (getIntent().hasExtra(DemoActivity.DEMO_FLAG)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			setContentView(R.layout.activity_main_demo);
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR |
					ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			setContentView(R.layout.activity_main);
		}

		this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		app = (App) getApplication();

		BluetoothManager bluetoothManager =
				(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetooth = bluetoothManager.getAdapter();

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
		findViewById(R.id.heart).setOnClickListener(this);
	}

	@Override
    protected void onStart() {
		Log.i(TAG, "onStart");
        super.onStart();
        // TODO(abhi): Create patchClient in onActivityResult if BT enable activity started.
     	patchClient = new BluetoothSmartClient(this, this);
     	patchClient.startScan();
    }

    @Override
    protected void onStop() {
    	Log.i(TAG, "onStop");
    	if (patchClient != null) {
    		patchClient.stopScan();
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
		if (chartUpdateTimer != null) {
			chartUpdateTimer.cancel();
		}
		if (bpmUpdateTimer != null) {
			bpmUpdateTimer.cancel();
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
				String positionTag = app.peekRecordingTags();
				if (getIntent().hasExtra(DemoActivity.DEMO_FLAG)) {
					positionTag = ((TextView) findViewById(R.id.tags)).getText().toString();
				}
				new PositionFragment(positionTag).show(getFragmentManager(), null);
			} else {
				stopRecording();
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
		case R.id.heart:
			if (player == null) {
				((TextView) findViewById(R.id.tap_to_listen)).setText(R.string.tap_to_mute);
				player = new  AudioPlayer();
				player.play();
			} else {
				((TextView) findViewById(R.id.tap_to_listen)).setText(R.string.tap_to_listen);
				player.release();
				player = null;
			}
			break;
		}
	}

	@Override
	public void onScanStart() {
		findViewById(R.id.progress).setVisibility(View.VISIBLE);
		findViewById(R.id.status).setVisibility(View.INVISIBLE);
		((TextView) findViewById(R.id.label_status)).setText(R.string.connecting);
	}

	@Override
	public void onScanResult(String deviceAddress) {
		patchClient.stopScan();
		patchClient.connect(deviceAddress);
	}

	@Override
	public void onScanEnd() {
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

		chartUpdateTimer = new Timer();
		chartUpdateTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				runOnUiThread(chartUpdater);
			}
		}, 0, Config.GRAPH_UPDATE_MILLIS);

		bpmUpdateTimer = new Timer();
		bpmUpdateTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				runOnUiThread(bpmUpdater);
			}
		}, 0, Config.BPM_UPDATE_MILLIS);
	}

	@Override
	public void onDisconnect(String address) {
		connected = false;
		Log.i(TAG, String.format("Disconnected from %s", address));
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				((ImageView) findViewById(R.id.status)).setImageResource(R.drawable.ic_warning);
				((TextView) findViewById(R.id.label_status)).setText(R.string.disconnected);
			}
		});
		chartUpdateTimer.cancel();
		bpmUpdateTimer.cancel();
	}

	@Override
	public void onServiceDiscovered(boolean success) {
		if (success && patchClient != null) {
			Log.i(TAG, "Notification service discoverd, enabling notifications");
			patchClient.enableNotifications();
		}
	}

	private final Runnable chartUpdater = new Runnable() {
		@Override
		public void run() {
			ChartFragment fragment =
					(ChartFragment) getFragmentManager().findFragmentById(R.id.chart);
			if (paused || fragment == null) {
				return;
			}

			fragment.clear();
			fragment.updateGraph(signals.getValues());
			// fragment.updateLongGraph(peaks);
			fragment.updateMarkers(
					signals.getFeatures(Feature.Type.QRS), signals.medianAmplitude);
		}
	};

	private final Runnable bpmUpdater = new Runnable() {
		@Override
		public void run() {
			((TextView) findViewById(R.id.bpm)).setText(
					signals.bpm == 0 ? "?" : Integer.toString(signals.bpm));
		}
	};

	@Override
	public void onNewValues(int chunk[]) {
		signals.update(chunk);

		if (writer != null) {
			writer.write(chunk);
		}

		audioBufferLength += chunk.length;
		if (audioBufferLength == Config.GRAPH_LENGTH) {
			audioBufferLength = 0;
			if (player != null) {
				player.write(signals);
			}
		}
	}

	public void startRecording() {
		startRecording(app.nextRecordingTags());
	}

	public void startRecording(String positionTag) {
		writer = new EcgDataWriter(this, positionTag);
		((ImageView) findViewById(R.id.record)).setImageResource(R.drawable.ic_action_stop);
		((TextView) findViewById(R.id.label_record)).setText(R.string.recording);
	}

	private void stopRecording() {
		writer.close();
		writer = null;
		((ImageView) findViewById(R.id.record)).setImageResource(R.drawable.ic_action_record);
		((TextView) findViewById(R.id.label_record)).setText(R.string.record);
		((TextView) findViewById(R.id.seconds)).setText("");
	}

	public void onRecordingUpdate(final long durationSeconds) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				((TextView) findViewById(R.id.seconds)).setText(Long.toString(durationSeconds));
			}
		});
	}
}
