package care.dovetail.monitor;

import java.util.Arrays;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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

	private final Context context;
	private final ConnectionListener listener;
	private BluetoothGattCharacteristic sensorData;

    public interface ConnectionListener {
    	public void onConnect(String address);
    	public void onDisconnect(String address);
    	public void onNewValues(float values[], boolean hasHeartBeat);
    }

	public BluetoothSmartClient(Context context, ConnectionListener listener) {
		this.context = context;
		this.listener = listener;
	}

	public void connectToDevice(String address) {
		if (address == null) {
			return;
		}
		Log.i(TAG, String.format("Connecting to BluetoothLE device %s.", address));
		final BluetoothManager bluetoothManager =
				(BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetooth = bluetoothManager.getAdapter();

		if (bluetooth != null && bluetooth.isEnabled()) {
			BluetoothDevice device = bluetooth.getRemoteDevice(address);
			device.connectGatt(context, true, this);
		}
	}

	@Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
        	Log.i(TAG, String.format("Connected to GATT server %s. "
        			+ "Attempting to start service discovery %b"
            		, gatt.getDevice().getAddress(), gatt.discoverServices()));
        	listener.onConnect(gatt.getDevice().getAddress());
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        	Log.e(TAG, String.format("Disconnected from GATT server %s.",
        			gatt.getDevice().getAddress()));
        	listener.onDisconnect(gatt.getDevice().getAddress());
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
        	Log.e(TAG, "onServicesDiscovered received: " + status);
        	return;
        }
    	Log.i(TAG, "onServicesDiscovered received: GATT_SUCCESS");
    	for (BluetoothGattService service : gatt.getServices()) {
    		Log.i(TAG, String.format("\nService UUID %s",
    				Long.toHexString(service.getUuid().getMostSignificantBits())));
    		for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
    			Log.i(TAG, String.format("Characteristic UUID %s Property %d",
    					Long.toHexString(characteristic.getUuid().getMostSignificantBits()),
    					characteristic.getProperties()));
    			if ((characteristic.getProperties()
    					& BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
        			gatt.setCharacteristicNotification(characteristic, true);
        			for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
        				descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            		    gatt.writeDescriptor(descriptor);
        			}
    			} else if (Long.toHexString(characteristic.getUuid().getMostSignificantBits())
    						.startsWith(Config.BT_SENSOR_DATA_CHAR_PREFIX)) {
    				sensorData = characteristic;
    			}
    		}
    	}
    }

    @Override
	public void onDescriptorWrite(BluetoothGatt gatt,
			BluetoothGattDescriptor descriptor, int status) {
		super.onDescriptorWrite(gatt, descriptor, status);
		Log.i(TAG, String.format("onDescriptorWrite: uuid %s status %d", descriptor.getUuid(),
				status));
	}

	@Override
	public void onCharacteristicRead(BluetoothGatt gatt,
			BluetoothGattCharacteristic characteristic, int status) {
		byte[] values = characteristic.getValue();
		Log.i(TAG, String.format("onCharacteristicRead new data: %s",
        		Arrays.toString(values)));
		float floatValues[] = new float[values.length];
		for (int i = 0; i < values.length; i++) {
			floatValues[i] = (float) ((values[i]) & 0xFF ) / 255;
		}
		listener.onNewValues(floatValues, false);
		super.onCharacteristicRead(gatt, characteristic, status);
	}

	@Override
	public void onCharacteristicChanged(BluetoothGatt gatt,
			BluetoothGattCharacteristic characteristic) {
        Log.i(TAG, String.format("onCharacteristicChanged new data: %s",
        		Arrays.toString(characteristic.getValue())));
        gatt.readCharacteristic(sensorData);
		super.onCharacteristicChanged(gatt, characteristic);
	}
}
