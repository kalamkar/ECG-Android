package care.dovetail.monitor;

import java.util.HashMap;
import java.util.Map;

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

public class PositionFragment extends DialogFragment {

	private static final Map<String, Integer> pictures = new HashMap<String, Integer>();

	static {
		pictures.put("top", R.drawable.belly_top);
		pictures.put("right", R.drawable.belly_right);
		pictures.put("bottom", R.drawable.belly_bottom);
		pictures.put("left", R.drawable.belly_left);
		pictures.put("top_far", R.drawable.belly_top_far);
		pictures.put("right_far", R.drawable.belly_right_far);
		pictures.put("bottom_far", R.drawable.belly_bottom_far);
		pictures.put("left_far", R.drawable.belly_left_far);
	}

	private final RecordingFragment recorder;
	private final String positionTags;
	private final String userTags;

	public PositionFragment(RecordingFragment recorder, String positionTags, String userTags) {
		this.recorder = recorder;
		this.positionTags = positionTags;
		this.userTags = userTags;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
		return inflater.inflate(R.layout.fragment_position, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		Integer positionPicture = pictures.get(positionTags);
		if (positionPicture == null) {
			recorder.startRecording(userTags);
			dismiss();
			return;
		}
		((ImageView) view.findViewById(R.id.image)).setImageResource(positionPicture);
		view.findViewById(R.id.start).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				// Burn the tags
				((App) getActivity().getApplication()).nextRecordingTags();
				recorder.startRecording(positionTags + "," + userTags);
				dismiss();
			}
		});
	}
}
