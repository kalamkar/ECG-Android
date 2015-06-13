package care.dovetail.monitor;

import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import care.dovetail.common.model.Event;

public class EventsFragment extends Fragment {
	private static final String TAG = "EventsFragment";

	private App app;
	private List<Event> events;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		app = (App) activity.getApplication();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		return inflater.inflate(R.layout.fragment_events, null);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
        events = app.events.getAll();
        ((ListView) view.findViewById(R.id.list)).setAdapter(new EventListAdapter());
		app.registerReceiver(receiver, new IntentFilter(Config.SERVICE_EVENT));
	}

    @Override
	public void onResume() {
		super.onResume();
		app.registerReceiver(receiver, new IntentFilter(Config.SERVICE_EVENT));
	}

	@Override
	public void onStop() {
		app.unregisterReceiver(receiver);
		super.onStop();
	}

    private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			reloadEvents();
		}
    };

    public void reloadEvents() {
    	if (app != null && app.events != null) {
    		events = app.events.getAll();
    	}
		((BaseAdapter) ((ListView) getView().findViewById(R.id.list))
				.getAdapter()).notifyDataSetChanged();
    }

	private class EventListAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return events.size();
		}

		@Override
		public Event getItem(int position) {
			return events.get(position);
		}

		@Override
		public long getItemId(int position) {
			return getItem(position).hashCode();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup container) {
			View view;
			if (convertView == null) {
				view = getActivity().getLayoutInflater().inflate(R.layout.list_item_event, null);
			} else {
				view = convertView;
			}

			Event event = getItem(position);
			view.setTag(event);
			int title = R.string.unknown;
			if (Event.Type.SERVICE_STARTED.name().equalsIgnoreCase(event.type)) {
				title = R.string.starting_service;
			} else if (Event.Type.KICK_RECORDED.name().equalsIgnoreCase(event.type)) {
				title = R.string.recorded_kick;
			} else if (Event.Type.KICK_DETECTED.name().equalsIgnoreCase(event.type)) {
				title = R.string.detected_kick;
			} else if (Event.Type.HEART_RATE.name().equalsIgnoreCase(event.type)) {
				title = R.string.heart_rate;
			} else if (Event.Type.SENSOR_CONNECTED.name().equalsIgnoreCase(event.type)) {
				title = R.string.baby_monitor_is_working;
			} else if (Event.Type.SENSOR_DISCONNECTED.name().equalsIgnoreCase(event.type)) {
				title = R.string.baby_monitor_is_not_working;
			} else if (event.type != null) {
				((TextView) view.findViewById(R.id.title)).setText(event.type);
			} else {
				Log.e(TAG, String.format("Unknown event %s", event.type));
			}
			if (title != R.string.unknown) {
				((TextView) view.findViewById(R.id.title)).setText(title);
			}
			((TextView) view.findViewById(R.id.hint)).setText(
					Config.EVENT_TIME_FORMAT.format(new Date(event.time)));
			return view;
		}
	}
}
