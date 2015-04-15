package care.dovetail.monitor;

import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class PairingActivity extends Activity {
	private static final String TAG = "PairingActivity";

	public static final String BTLE_ADDRESS = "BTLE_ADDRESS" ;

	private BluetoothAdapter bluetooth;
	private BluetoothLeScanner scanner;

	private Handler handler = new Handler();

	private List<ScanResult> scans = new ArrayList<ScanResult>();

	private final DeviceListAdapter adapter = new DeviceListAdapter();

	// Stops scanning after 10 seconds.
	private static final int REQUEST_ENABLE_BT = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_pairing);
		((ListView) findViewById(R.id.list)).setAdapter(adapter);

		final BluetoothManager bluetoothManager =
				(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		bluetooth = bluetoothManager.getAdapter();
		scanner = bluetooth.getBluetoothLeScanner();

		if (bluetooth == null || !bluetooth.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		scanLeDevice();
	}

	@Override
	protected void onDestroy() {
		stopScan();
		super.onDestroy();
	}

	private void scanLeDevice() {
		scans.clear();
		ScanSettings settings = new ScanSettings.Builder()
				.setScanMode(ScanSettings.SCAN_MODE_BALANCED).setReportDelay(0).build();
		scanner.startScan(null, settings, callback);
	}

	private ScanCallback callback = new ScanCallback() {
		@Override
		public void onScanFailed(int errorCode) {
			Toast.makeText(PairingActivity.this,
					String.format("Bluetooth LE scan failed with error %d", errorCode),
					Toast.LENGTH_LONG).show();
			super.onScanFailed(errorCode);
		}

		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			scans.add(result);
			adapter.notifyDataSetChanged();
			super.onScanResult(callbackType, result);
		}
	};

	private void stopScan() {
		scanner.stopScan(callback);
	}

	private class DeviceListAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return scans != null ? scans.size() : 0;
		}

		@Override
		public ScanResult getItem(int position) {
			return scans.get(position);
		}

		@Override
		public long getItemId(int position) {
			return getItem(position).hashCode();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup container) {
			View view;
			if (convertView == null) {
				view = getLayoutInflater().inflate(R.layout.list_item_device, null);
			} else {
				view = convertView;
			}

			view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					ScanResult scan = (ScanResult) view.getTag();
					PairingActivity.this.setResult(RESULT_OK,
							getIntent().putExtra(BTLE_ADDRESS, scan.getDevice().getAddress()));
					PairingActivity.this.finish();
				}
			});
			ScanResult scan = getItem(position);
			view.setTag(scan);
			String name = scan.getDevice().getName();
			name = name == null ? scan.getScanRecord().getDeviceName() : name;
			name = name == null ? scan.getDevice().getAddress() : name;
			((TextView) view.findViewById(R.id.title)).setText(name);
			((TextView) view.findViewById(R.id.hint)).setText(scan.getDevice().getAddress());
			return view;
		}
	}
}
