package care.dovetail.monitor;

import java.util.Timer;
import java.util.TimerTask;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import care.dovetail.monitor.BluetoothSmartClient.BluetoothDeviceListener;
import care.dovetail.monitor.SignalProcessor.Feature;
import care.dovetail.monitor.ui.BreathFragment;
import care.dovetail.monitor.ui.EcgFragment;
import care.dovetail.monitor.ui.RecordingFragment;

public class MainActivity extends FragmentActivity
		implements BluetoothDeviceListener, OnClickListener {
	private static final String TAG = "MainActivity";

	private BluetoothSmartClient patchClient;
	private final SignalProcessor signals = new SignalProcessor();

	private int audioBufferLength = 0;
	private AudioPlayer player;

	private RecordingFragment recorder;

	private Timer chartUpdateTimer = null;
	private Timer bpmUpdateTimer = null;

	private Fragment fragments[] = { new EcgFragment(), new BreathFragment() };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
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

		BluetoothManager bluetoothManager =
				(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetooth = bluetoothManager.getAdapter();

		if (bluetooth == null || !bluetooth.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, 0);
		}

		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
		    Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG).show();
		}

		findViewById(R.id.heart).setOnClickListener(this);

		((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener(
				new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton button, boolean isChecked) {
						if (isChecked && recorder != null) {
							recorder.stopRecording();
						}
					}
		});

		((ViewPager) findViewById(R.id.pager)).setAdapter(
				new ChartPagerAdapter(getSupportFragmentManager()));;
	}

	@Override
    protected void onStart() {
        super.onStart();
        // TODO(abhi): Create patchClient in onActivityResult if BT enable activity started.
     	patchClient = new BluetoothSmartClient(this, this);
     	patchClient.startScan();
    }

    @Override
    protected void onStop() {
    	if (patchClient != null) {
    		patchClient.stopScan();
    		patchClient.close();
    		patchClient = null;
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
		if (patchClient == null || !patchClient.isConnected()) {
			return;
		}
		switch(view.getId()) {
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
		((TextView) findViewById(R.id.status)).setText(R.string.connecting);
	}

	@Override
	public void onScanResult(String deviceAddress) {
		patchClient.stopScan();
		patchClient.connect(deviceAddress);
	}

	@Override
	public void onScanEnd() {
		findViewById(R.id.progress).setVisibility(View.GONE);
	}

	@Override
	public void onConnect(String address) {
		Log.i(TAG, String.format("Connected to %s", address));
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (MainActivity.this.isDestroyed()) {
					return;
				}
				((TextView) findViewById(R.id.status)).setText(R.string.connected);

				recorder = new RecordingFragment();
				getFragmentManager().beginTransaction().add(R.id.recorder, recorder).commit();
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
		Log.i(TAG, String.format("Disconnected from %s", address));
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (MainActivity.this.isDestroyed()) {
					recorder = null;
					return;
				}
				((TextView) findViewById(R.id.status)).setText(R.string.disconnected);
				getFragmentManager().beginTransaction().remove(recorder).commit();
			}
		});
		if (chartUpdateTimer != null) {
			chartUpdateTimer.cancel();
		}
		if (bpmUpdateTimer != null) {
			bpmUpdateTimer.cancel();
		}
	}

	private final Runnable chartUpdater = new Runnable() {
		@Override
		public void run() {
			if (MainActivity.this.isDestroyed()
					|| ((ToggleButton) findViewById(R.id.pause)).isChecked()) {
				return;
			}

			boolean filter = ((ToggleButton) findViewById(R.id.filter)).isChecked();
			((EcgFragment) fragments[0]).clear();
			((EcgFragment) fragments[0]).update(
					filter ? signals.getFilteredValues() : signals.getValues(),
					signals.getFeatures(Feature.Type.QRS), signals.medianAmplitude);
			((BreathFragment) fragments[1]).clear();
			((BreathFragment) fragments[1]).update(signals.getBreathValues());
		}
	};

	private final Runnable bpmUpdater = new Runnable() {
		@Override
		public void run() {
			int bpm = signals.getBpm();
			((TextView) findViewById(R.id.bpm)).setText(bpm == 0 ? "?" : Integer.toString(bpm));
		}
	};

	@Override
	public void onNewValues(int chunk[]) {
		if (recorder != null) {
			recorder.record(chunk);
		}
		signals.update(chunk);

		audioBufferLength += chunk.length;
		if (audioBufferLength == Config.GRAPH_LENGTH) {
			audioBufferLength = 0;
			if (player != null) {
				player.write(signals);
			}
		}
	}

	private class ChartPagerAdapter extends FragmentStatePagerAdapter {
        public ChartPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return fragments[position];
        }

        @Override
        public int getCount() {
            return fragments.length;
        }
    }
}
