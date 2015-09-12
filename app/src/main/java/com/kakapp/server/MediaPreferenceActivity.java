package com.kakapp.server;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.TwoStatePreference;
import android.text.util.Linkify;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;

public class MediaPreferenceActivity extends PreferenceActivity {

	private static String TAG = MediaPreferenceActivity.class.getSimpleName();
	private static final String AD_UNIT_ID = "ca-app-pub-8092647504892778/1292340041";
	private Handler mHandler = new Handler();

	private AdRequest adRequest;

	private InterstitialAd interstitialAd;

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "created");
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		TwoStatePreference runningPref = findPref("running_switch");

		updateRunningState();

		runningPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((Boolean) newValue) {
					startServer();
				} else {
					stopServer();
				}
				return true;
			}
		});

		Preference selectPref = findPref("media_select");
		selectPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if (!MediaUpnpService.isRunning()) {
					startActivity(new Intent(MediaPreferenceActivity.this, MainActivity.class));
				}

				return false;
			}
		});

		EditTextPreference deName_pref = findPref("deName");
		deName_pref.setSummary(MediaSettings.getDeviceName());
		deName_pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String newNameString = (String) newValue;
				if (preference.getSummary().equals(newNameString))
					return false;
				preference.setSummary(newNameString);
				stopServer();
				return true;
			}
		});

		Preference help = findPref("help");
		help.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Log.v(TAG, "On preference help clicked");
				Context context = MediaPreferenceActivity.this;
				AlertDialog ad = new AlertDialog.Builder(context).setTitle(R.string.help_dlg_title).setMessage(R.string.help_dlg_message).setPositiveButton(R.string.ok, null).create();
				ad.show();
				Linkify.addLinks((TextView) ad.findViewById(android.R.id.message), Linkify.ALL);
				return true;
			}
		});

		Preference about = findPref("about");
		about.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				AlertDialog ad = new AlertDialog.Builder(MediaPreferenceActivity.this).setTitle(R.string.about_dlg_title).setMessage(R.string.about_dlg_message)
						.setPositiveButton(getText(R.string.ok), null).create();
				ad.show();
				Linkify.addLinks((TextView) ad.findViewById(android.R.id.message), Linkify.ALL);
				return true;
			}
		});

		adRequest = new AdRequest.Builder().addTestDevice("FC30F813719E71A110A143F708B6C212").addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();

		interstitialAd = new InterstitialAd(this);
		interstitialAd.setAdUnitId(AD_UNIT_ID);
		interstitialAd.setAdListener(new AdListener() {
			@Override
			public void onAdLoaded() {
				Log.d("AdListener", "onAdLoaded");
				interstitialAd.show();
			}

			@Override
			public void onAdFailedToLoad(int errorCode) {
				String message = String.format("onAdFailedToLoad (%s)", getErrorReason(errorCode));
				Log.d("AdListener", message);
			}
		});

		interstitialAd.loadAd(adRequest);

	}

	@Override
	protected void onResume() {
		super.onResume();

		updateRunningState();

		Log.v(TAG, "onResume: Registering the server actions");

		IntentFilter filter = new IntentFilter();
		filter.addAction(MediaUpnpService.ACTION_STARTED);
		filter.addAction(MediaUpnpService.ACTION_STOPPED);
		filter.addAction(MediaUpnpService.ACTION_FAILEDTOSTART);
		registerReceiver(mFsActionsReceiver, filter);
	}

	@Override
	protected void onPause() {
		super.onPause();

		Log.v(TAG, "onPause: Unregistering the Server actions");
		unregisterReceiver(mFsActionsReceiver);

	}

	private void startServer() {
		sendBroadcast(new Intent(MediaUpnpService.ACTION_START_SERVER));
	}

	private void stopServer() {
		sendBroadcast(new Intent(MediaUpnpService.ACTION_STOP_SERVER));
	}

	private void updateRunningState() {
		TwoStatePreference runningPref = findPref("running_switch");
		Preference selectPref = findPref("media_select");
		CheckBoxPreference videoPref = findPref("allow_video");
		CheckBoxPreference audioPref = findPref("allow_audio");
		CheckBoxPreference imagePref = findPref("allow_image");
		if (MediaUpnpService.isRunning() == true) {
			videoPref.setEnabled(false);
			audioPref.setEnabled(false);
			imagePref.setEnabled(false);
			selectPref.setEnabled(false);
			runningPref.setSummary(R.string.running_summary_started);
			selectPref.setSummary(R.string.running_select_summary);
		} else {
			runningPref.setChecked(false);
			runningPref.setSummary(R.string.running_summary_stopped);
			selectPref.setSummary(R.string.select_summary);
			videoPref.setEnabled(true);
			audioPref.setEnabled(true);
			imagePref.setEnabled(true);
			selectPref.setEnabled(true);
		}
	}

	BroadcastReceiver mFsActionsReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.v(TAG, "action received: " + intent.getAction());
			// action will be ACTION_STARTED or ACTION_STOPPED
			updateRunningState();
			// or it might be ACTION_FAILEDTOSTART
			final TwoStatePreference runningPref = findPref("running_switch");
			if (intent.getAction().equals(MediaUpnpService.ACTION_FAILEDTOSTART)) {
				runningPref.setChecked(false);
				mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						runningPref.setSummary(R.string.running_summary_failed);
					}
				}, 100);
				mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						runningPref.setSummary(R.string.running_summary_stopped);
					}
				}, 5000);
			}
		}
	};

	@SuppressWarnings({ "unchecked", "deprecation" })
	protected <T extends Preference> T findPref(CharSequence key) {
		return (T) this.findPreference(key);
	}

	private String getErrorReason(int errorCode) {
		String errorReason = "";
		switch (errorCode) {
		case AdRequest.ERROR_CODE_INTERNAL_ERROR:
			errorReason = "Internal error";
			break;
		case AdRequest.ERROR_CODE_INVALID_REQUEST:
			errorReason = "Invalid request";
			break;
		case AdRequest.ERROR_CODE_NETWORK_ERROR:
			errorReason = "Network Error";
			break;
		case AdRequest.ERROR_CODE_NO_FILL:
			errorReason = "No fill";
			break;
		}
		return errorReason;
	}

}
