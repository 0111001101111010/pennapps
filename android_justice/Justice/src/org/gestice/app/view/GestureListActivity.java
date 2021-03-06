package org.gestice.app.view;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.gestice.app.model.GestureCellModel;
import org.gestice.app.service.JusticeService;
import org.gestice.app.view.settings.SettingsActivity;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import org.gestice.app.R;

import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class GestureListActivity extends Activity {

	public static final String TAG = "MainActivity";
	public static final UUID JUSTICE_APP_UUID = UUID.fromString("259047ec-66dc-4f44-b3b5-1d1477fc7a90");
	
	private ListView mList;
	private GestureCellAdapter mAdapter;
	private ArrayList<GestureCellModel> mData;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gesture_list_activity);
		
		// Get references to UI components
		mList = (ListView)findViewById(android.R.id.list);

		// Start a service to listen for Pebble data
		Intent serviceIntent = new Intent(this, JusticeService.class);
		startService(serviceIntent);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		// We load the data in onResume rather than in onCreate because onResume is called each time the activity
		// is shown again. Most importantly, when we arrive back at this list after having edited an item, this method
		// will be called to load the changes.

		if (!loadGestureCellData()) {
			final PackageManager manager = getPackageManager();
			Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
			mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
			List<ResolveInfo> applicationData = manager.queryIntentActivities(mainIntent, 0);

			// There is no data saved, so create some here and commit it
			mData = new ArrayList<GestureCellModel>();
			String[] gestures = { "X-Axis Shake", "Y-Axis Shake", "Z-Axis Shake" };
			for (int i = 0; i < gestures.length; i++) {
				// Just use the top three apps from the queryIntentActivities call...
				ResolveInfo data = applicationData.get(i);
				GestureCellModel model = new GestureCellModel();

				model.gestureName = gestures[i];
				model.name = (String)data.activityInfo.loadLabel(manager);
				model.packageName = data.activityInfo.packageName;
				mData.add(model);
			}
			saveGestureCellData();
		}

		// Initialize the adapter and hook it up to the list
		mAdapter = new GestureCellAdapter(this, R.layout.gesture_cell);
		mAdapter.setData(mData);
		mList.setAdapter(mAdapter);

		// Start the Justice pebble app
		Log.i(TAG, "starting app on pebble");
		PebbleKit.startAppOnPebble(getApplicationContext(), JUSTICE_APP_UUID);
		
		// Send data to pebble
		// Note: Do this in a handler because the message sometimes gets to the device
		// before the application has loaded, which is no bueno
		final Activity activity = this;
		new Handler().postDelayed(new Runnable() {
			public void run() {
				PebbleDictionary data = new PebbleDictionary();
				data.addString(100, "apps");
				for (int i = 0; i < 3; i++) {
					data.addString(i, mData.get(i).name);
				}
				PebbleKit.sendDataToPebble(activity, JUSTICE_APP_UUID, data);
				Log.i(TAG, "sending " + data.toJsonString() + " to pebble");
			}
		}, 1000);
		
		// Set up click handler
		mList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// Launch a GestureEditActivity to select a new app for this gesture
				Intent intent = new Intent(activity, GestureEditActivity.class);

				// Tell the new activity what gesture position to edit
				intent.putExtra(GestureEditActivity.POSITION, position);
				startActivity(intent);
				
				overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_right);
			}
		});
		mAdapter.notifyDataSetChanged();
	}
	
	// Returns true if there was saved data, false if nothing was there
	public boolean loadGestureCellData() {
		// Get a string from SharedPreferences and convert it to a real Java object array
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String json = prefs.getString("gestures", null);
		Log.d(TAG, "loaded json: " + json);
		if (json != null) {
			mData = GestureCellModel.arrayFromString(json);
			return true;
		} else {
			return false;
		}
	}
	
	public void saveGestureCellData() {
		// Convert our data array to a JSON Array string and save it in SharedPreferences
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		String json = GestureCellModel.toJSONArrayString(mData);
		Log.d(TAG, "saving json: " + json);
		editor.putString("gestures", json);
		editor.commit();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.settings) {
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		}
		return false;
	}
	
	public static class GestureCellAdapter extends ArrayAdapter<GestureCellModel> {
		
		private Context mContext;
		private int mResource;
		private ArrayList<GestureCellModel> mData = new ArrayList<GestureCellModel>();

		public GestureCellAdapter(Context context, int resource) {
			super(context, resource);
			mContext = context;
			mResource = resource;
		}
		
		public void setData(ArrayList<GestureCellModel> data) {
			mData = data;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				LayoutInflater inflater = LayoutInflater.from(mContext);
				convertView = inflater.inflate(mResource, parent, false);
			}
			
			// Get UI components
			TextView gestureName = (TextView)convertView.findViewById(R.id.gestureName);
			ImageView appIcon = (ImageView)convertView.findViewById(R.id.appIcon);
			TextView appName = (TextView)convertView.findViewById(R.id.appName);

			// Get the data for the item and set up the view
			GestureCellModel model = mData.get(position);
			
			gestureName.setText(model.gestureName);
			if (model.packageName != null && !model.packageName.equals("")) {
				// If this is a true application, show it's label and icon
				PackageManager packageManager = mContext.getPackageManager();
				Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
				mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
				mainIntent.setPackage(model.packageName);
				ResolveInfo info = packageManager.queryIntentActivities(mainIntent, 0).get(0);
	
				if (info != null) {
					// Get app data --- name and icon
					appName.setText(info.activityInfo.applicationInfo.loadLabel(packageManager));
					appIcon.setImageDrawable(info.activityInfo.applicationInfo.loadIcon(packageManager));
				}
			}

			return convertView;
		}
		
		@Override
		public int getCount() {
			return mData.size();
		}
	}
}
