package care.dovetail.monitor;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import care.dovetail.common.model.Event;

@SuppressLint("NewApi")
public class BackgroundService extends Service {
	private static final String TAG = "BackgroundService";

	private App app;
	private SharedPreferences prefs;
	private BluetoothSmartClient bluetooth;

	private BeatCounter beatCounter;

    private final IBinder binder = new LocalBinder();
    private NotificationManager notifications;

    public interface ValuesListener {
    	public void onNewValues(float values[], boolean hasHeartBeat);
    }

	private ValuesListener listener = new ValuesListener() {
		@Override
		public void onNewValues(float values[], boolean hasHeartBeat) {
			sendBroadcast(new Intent(Config.SERVICE_DATA)
					.putExtra(Config.SENSOR_DATA_HEARTBEAT, hasHeartBeat)
					.putExtra(Config.SENSOR_DATA, values));
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		app = (App) getApplication();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			initBluetooth();
		}

		showNotification(R.string.app_name, R.string.baby_monitor_is_not_working,
				Event.Type.SERVICE_STARTED.name(), R.drawable.ic_service_error);
	}

	private void initBluetooth() {
		bluetooth = new BluetoothSmartClient(app, listener);
		prefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);
		prefs.registerOnSharedPreferenceChangeListener(
				new SharedPreferences.OnSharedPreferenceChangeListener() {
					@Override
					public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
						bluetooth.connectToDevice(app.settings.getBluetoothAddress());
					}});
		bluetooth.connectToDevice(app.settings.getBluetoothAddress());
	}

	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }

	@Override
	public void onDestroy() {
		if (notifications != null) {
			notifications.cancelAll();
		}
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	public class LocalBinder extends Binder {
		BackgroundService getService() {
            return BackgroundService.this;
        }
    }

	public int getBeatsPerMinute() {
		return beatCounter != null ? beatCounter.getBeatsPerMinute() : 0;
	}

	private void broadcastEvent(String type, long timeMillis) {
		sendBroadcast(new Intent(Config.SERVICE_EVENT).putExtra(Config.EVENT_TYPE, type)
				.putExtra(Config.EVENT_TIME, timeMillis));
		app.events.add(new Event(type, timeMillis));
	}

	private void showNotification(int title, int text, String eventType, int icon) {
		broadcastEvent(eventType, System.currentTimeMillis());
		if (notifications == null) {
			notifications = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		} else {
			notifications.cancelAll();
		}
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);
        Notification notification = new Notification.Builder(this)
        	.setSmallIcon(icon)
        	.setContentTitle(getText(title))
        	.setContentText(getText(text))
        	.setAutoCancel(false)
        	.setOngoing(true)
        	.setContentIntent(contentIntent)
        	.build();
        notifications.notify(text, notification);
    }
}
