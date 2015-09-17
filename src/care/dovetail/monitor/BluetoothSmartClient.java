package care.dovetail.monitor;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothSmartClient extends BluetoothGattCallback {
	private static final String TAG = "BluetoothSmartClient";

	private final ConnectionListener listener;

	private final BluetoothAdapter adapter;

	private BluetoothGatt gatt;
	private BluetoothGattCharacteristic sensorData;
	private BluetoothGattCharacteristic peakValue;

	private int state = BluetoothProfile.STATE_DISCONNECTED;

    public interface ConnectionListener {
    	public void onConnect(String address);
    	public void onDisconnect(String address);
    	public void onServiceDiscovered(boolean success);
    	public void onNewValues(int values[]);
    }

	public BluetoothSmartClient(Context context, ConnectionListener listener, String address) {
		this.listener = listener;
		BluetoothManager bluetoothManager =
				(BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		adapter = bluetoothManager.getAdapter();

		if (address == null || address.isEmpty()) {
			Log.e(TAG, "No BluetoothLE device given to connect.");
			return;
		}
		if (adapter != null && adapter.isEnabled()) {
			adapter.getRemoteDevice(address).connectGatt(context, false /* auto connect */, this);
		}
	}

	@Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        this.gatt = gatt;
        this.state = newState;
        if (newState == BluetoothProfile.STATE_CONNECTED) {
        	gatt.discoverServices();
        	listener.onConnect(gatt.getDevice().getAddress());
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        	listener.onDisconnect(gatt.getDevice().getAddress());
        } else {
        	Log.e(TAG, String.format("GATT server %s changed to unknown new state %d and status %d",
        			gatt.getDevice().getAddress(), newState, status));
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
        	Log.e(TAG, "onServicesDiscovered received error code: " + status);
        	return;
        }
    	for (BluetoothGattService service : gatt.getServices()) {
    		for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
    			if ((characteristic.getProperties()
    					& BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
    				peakValue = characteristic;
    			} else if (Long.toHexString(characteristic.getUuid().getMostSignificantBits())
    						.startsWith(Config.BT_SENSOR_DATA_CHAR_PREFIX)) {
    				sensorData = characteristic;
    			}
    		}
    	}
    	if (peakValue != null && sensorData != null) {
    		Log.d(TAG, String.format("Found PeakValue UUID %s and Sensor Data UUID %s",
    				Long.toHexString(peakValue.getUuid().getMostSignificantBits()),
    				Long.toHexString(sensorData.getUuid().getMostSignificantBits())));
    		listener.onServiceDiscovered(true);
    	} else {
    		Log.e(TAG, "Could not find PeakValue and/or Sensor Data.");
    		listener.onServiceDiscovered(false);
    	}
    }

	@Override
	public void onCharacteristicRead(BluetoothGatt gatt,
			BluetoothGattCharacteristic characteristic, int status) {
		byte[] values = characteristic.getValue();
		int intValues[] = new int[values.length];
		for (int i = 0; i < values.length; i++) {
			intValues[i] = values[i] & 0xFF;
		}
//		Log.v(TAG, String.format("onCharacteristicRead new data: %s", Arrays.toString(intValues)));
		listener.onNewValues(intValues);
		super.onCharacteristicRead(gatt, characteristic, status);
	}

	@Override
	public void onCharacteristicChanged(BluetoothGatt gatt,
			BluetoothGattCharacteristic characteristic) {
//        Log.v(TAG, String.format("onCharacteristicChanged new data: %s",
//        		Arrays.toString(characteristic.getValue())));
        gatt.readCharacteristic(sensorData);
		super.onCharacteristicChanged(gatt, characteristic);
	}

	public boolean isConnected() {
		return state == BluetoothProfile.STATE_CONNECTED;
	}

	public String getDevice() {
		return gatt == null ? null : gatt.getDevice().getAddress();
	}

	public void disconnect() {
		if (gatt != null && isConnected()) {
			gatt.disconnect();
		}
		gatt = null;
	}

	public boolean enableNotifications() {
		if (peakValue == null) {
			return false;
		}
		boolean success = true;
		success = success && gatt.setCharacteristicNotification(peakValue, true);
		for (BluetoothGattDescriptor descriptor : peakValue.getDescriptors()) {
			descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			success = success && gatt.writeDescriptor(descriptor);
		}
		return success;
	}

	public boolean disableNotifications() {
		if (peakValue == null) {
			return false;
		}
		boolean success = true;
		success = success && gatt.setCharacteristicNotification(peakValue, false);
		for (BluetoothGattDescriptor descriptor : peakValue.getDescriptors()) {
			descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
			success = success && gatt.writeDescriptor(descriptor);
		}
		return success;
	}
}
