package care.dovetail.monitor;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import care.dovetail.monitor.messaging.GCMUtils;

public class MainActivity extends FragmentActivity {
	private static final String TAG = "MainActivity";

	private static final int PAIRING_ACTIVITY = 0;

	private App app;
	private PagerAdapter adapter;

	private final Fragment fragments[] = { new MainFragment(), new EventsFragment() };

	private final int titleIds[] = { R.string.home, R.string.events };


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        app = (App) getApplication();

		if (GCMUtils.checkPlayServices(this)) {
			app.requestRegistrationId();
		}

        adapter = new PagerAdapter(this.getSupportFragmentManager());
		((ViewPager) findViewById(R.id.pager)).setAdapter(adapter);
	}

    @SuppressLint("InlinedApi")
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		menu.findItem(R.id.action_bt_scan).setEnabled(android.os.Build.VERSION.SDK_INT
				>= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2
				&& getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE));
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.action_bt_scan:
			if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
				startActivityForResult(new Intent(this, PairingActivity.class), PAIRING_ACTIVITY);
			}
			break;
		case R.id.action_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			break;
		case R.id.action_disconnect:
			app.settings.setBluetoothAddress("");
			break;
		case R.id.action_wipe_db:
			app.events.reset();
			reloadEvents();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != RESULT_OK || data == null) {
			return;
		}
		switch(requestCode) {
		case PAIRING_ACTIVITY:
			String address = data.getStringExtra(PairingActivity.BTLE_ADDRESS);
			app.settings.setBluetoothAddress(address);
			Log.i(TAG, String.format("BluetoothLE address %s selected.", address));
			break;
		}
	}

    public void reloadEvents() {
    	((EventsFragment) fragments[1]).reloadEvents();
    }

    private class PagerAdapter extends FragmentStatePagerAdapter {
		public PagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			return fragments[position];
		}

		@Override
	    public CharSequence getPageTitle(int position) {
	        return getResources().getString(titleIds[position]);
	    }

		@Override
		public int getCount() {
			return fragments.length;
		}
	}
}
