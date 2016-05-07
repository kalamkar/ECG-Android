package care.dovetail.monitor;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

public class RecordingFragment extends Fragment {
	private static final String TAG = "RecordingFragment";

	private App app;
	private EcgDataWriter writer = null;

	private Timer recordingTimer = null;
	private long recordingStartTime = 0;

	private TextView record;
	private EditText tags;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		app = (App) activity.getApplication();
	}

	@Override
	public void onStop() {
		stopRecording();
		super.onStop();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_recording, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		tags = (EditText) view.findViewById(R.id.tags);
		tags.setText(app.getUserTags());
		tags.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable editable) {
				app.setUserTags(editable.toString());
			}

			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}

			@Override
			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
		});
		((TextView) view.findViewById(R.id.recordingIndex)).setText(app.getRecordingIndex());

		record = (TextView) view.findViewById(R.id.record);
		record.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (writer == null) {
					String userTags = tags.getText().toString();
					String positionTags = "demo";
					if (!getActivity().getIntent().hasExtra(DemoActivity.DEMO_FLAG)) {
						positionTags = app.peekRecordingTags();
					}
					new PositionFragment(RecordingFragment.this, positionTags, userTags)
							.show(getFragmentManager(), null);
				} else {
					stopRecording();
				}
			}
		});

		record.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View arg0) {
				if (writer != null) {
					return false;
				}
				app.resetRecordingTags();
				((TextView) getView().findViewById(R.id.recordingIndex)).setText(
						app.getRecordingIndex());
				return true;
			}
		});
	}

	public void startRecording(String positionTag) {
		writer = new EcgDataWriter(app, positionTag);
		record.setText(R.string.recording);
		record.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_action_stop, 0, 0);

        recordingStartTime = System.currentTimeMillis();
		recordingTimer = new Timer();
		recordingTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						long seconds = (System.currentTimeMillis() - recordingStartTime) / 1000;
						((TextView) getView().findViewById(R.id.seconds)).setText(
								Long.toString(seconds));
					}
				});
			}
		}, 0, 1000);
	}

	public void record(int chunk[]) {
		if (writer != null) {
			writer.write(chunk);
		}
	}

	public void stopRecording() {
		if (writer == null) {
			return;
		}
		writer.close();
		writer = null;
		recordingTimer.cancel();
		record.setText(R.string.record);
		record.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_action_record, 0, 0);
		((TextView) getView().findViewById(R.id.seconds)).setText("");
		((TextView) getView().findViewById(R.id.recordingIndex)).setText(app.getRecordingIndex());
	}
}
