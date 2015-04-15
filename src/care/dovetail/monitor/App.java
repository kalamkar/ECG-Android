package care.dovetail.monitor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.UUID;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.message.BasicNameValuePair;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import care.dovetail.common.ApiResponseTask;
import care.dovetail.common.model.ApiResponse;
import care.dovetail.monitor.messaging.GCMUtils;

import com.google.android.gms.gcm.GoogleCloudMessaging;

public class App extends Application {
	private static final String TAG = "App";

	private static final String DEVICE_ID = "DEVICE_ID";
	private static final String LAST_SYNC_TIME = "LAST_SYNC_TIME";

	public final EventDB events = new EventDB(this);

	private String registrationId;
	private GoogleCloudMessaging gcm;
	public final Settings settings = new Settings(this);

	private final SyncTask syncTask = new SyncTask(this);

	@Override
	public void onCreate() {
		super.onCreate();
		startService(new Intent(this, BackgroundService.class));

		new Timer().scheduleAtFixedRate(syncTask, Config.EVENT_SYNC_INTERVAL_MILLIS,
				Config.EVENT_SYNC_INTERVAL_MILLIS);

		if (getDeviceId() == null) {
			createDeviceId();
		}
	}

	public void setLastSyncTime(long timeMillis) {
		final Editor editor = getSharedPreferences(getPackageName(), MODE_PRIVATE).edit();
		new AsyncTask<Long, Void, Void>() {
			@Override
			protected Void doInBackground(Long... timeMillis) {
				if (timeMillis != null && timeMillis.length > 0) {
					editor.putLong(LAST_SYNC_TIME, timeMillis[0]);
					editor.commit();
				}
				return null;
			}
		}.execute(timeMillis);
	}

	public long getLastSyncTime() {
		return getSharedPreferences(
				getPackageName(), MODE_PRIVATE).getLong(LAST_SYNC_TIME, 0);
	}

	private void createDeviceId() {
		final Editor editor = getSharedPreferences(getPackageName(), MODE_PRIVATE).edit();
		new AsyncTask<String, Void, Void>() {
			@Override
			protected Void doInBackground(String... addresses) {
				if (addresses != null && addresses.length > 0) {
					editor.putString(DEVICE_ID, addresses[0]);
					editor.commit();
				}
				return null;
			}

			@SuppressWarnings("unchecked")
			@Override
			protected void onPostExecute(Void result) {
				if (registrationId != null) {
					new RegisterDevice().execute();
				}
			}
		}.execute(UUID.randomUUID().toString());
	}

	public String getDeviceId() {
		return getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(DEVICE_ID, null);
	}

	@SuppressWarnings("unchecked")
	public void requestRegistrationId() {
		if (registrationId != null && getDeviceId() != null) {
			Log.i(TAG, "Device already registered, registration ID = " + registrationId);
			new RegisterDevice().execute();
			return;
		}
	    new AsyncTask<Void, Void, Void>() {
			@Override
	        protected Void doInBackground(Void... params) {
	            try {
	            	if (gcm == null) {
	                    gcm = GoogleCloudMessaging.getInstance(App.this);
	                }
	                registrationId = gcm.register(Config.GCM_SENDER_ID);
	                Log.i(TAG, "Device registered, registration ID = " + registrationId);
	                if (getDeviceId() != null) {
	                	new RegisterDevice().execute();
	                }
	                GCMUtils.storeRegistrationId(App.this, registrationId);
	            } catch (IOException ex) {
	                Log.w(TAG, ex);
	            }
	            return null;
	        }
	    }.execute(null, null, null);
	}

	private class RegisterDevice extends ApiResponseTask {
		@Override
		protected HttpRequestBase makeRequest(Pair<String, String>... params)
				throws UnsupportedEncodingException {
			List<NameValuePair> queryParams = new ArrayList<NameValuePair>();
			queryParams.add(new BasicNameValuePair("id", getDeviceId()));
			queryParams.add(new BasicNameValuePair("type", "GOOGLE"));
			queryParams.add(new BasicNameValuePair("token", registrationId));
			queryParams.add(new BasicNameValuePair("model", String.format("%s %s",
					Build.MANUFACTURER, Build.MODEL)));
			for (Pair<String, String> param : params) {
				queryParams.add(new BasicNameValuePair(param.first, param.second));
			}
			HttpPost request = new HttpPost(Config.DEVICE_URL);
			request.setEntity(new UrlEncodedFormEntity(queryParams));
			return request;
		}

		@Override
		protected void onPostExecute(ApiResponse result) {
			super.onPostExecute(result);
			if (result != null && !"OK".equalsIgnoreCase(result.code)) {
				Log.e(TAG, result.message);
			}
		}
	}
}
