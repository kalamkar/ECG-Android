package care.dovetail.monitor;

import java.io.File;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.app.Fragment;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import care.dovetail.common.model.Event;

public class MainFragment extends Fragment {

	private App app;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		app = (App) activity.getApplication();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		return inflater.inflate(R.layout.fragment_main, null);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		view.findViewById(R.id.kick).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				app.events.add(
						new Event(Event.Type.KICK_RECORDED.name(), System.currentTimeMillis()));
				((MainActivity) getActivity()).reloadEvents();
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		updateAvailableSpace();
	}

	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	private void updateAvailableSpace() {
		File downloads = Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_DOWNLOADS);
		StatFs stat = new StatFs(downloads.getPath());
		long size = 0;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			size = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
		} else {
			size = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
		}
		((TextView) getView().findViewById(R.id.availableSpace)).setText(
				Formatter.formatFileSize(getActivity(), size));
	}
}
