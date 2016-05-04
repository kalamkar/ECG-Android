package care.dovetail.monitor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class DemoActivity extends Activity {
	public static final String DEMO_FLAG = "DEMO";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startActivityForResult(new Intent(this, MainActivity.class).putExtra(DEMO_FLAG, true), 0);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		finish();
	}
}
