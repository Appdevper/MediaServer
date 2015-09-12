

package com.kakapp.server;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class AppController extends Application {

	private static final String TAG = AppController.class.getSimpleName();

	private static Context sContext;

	@Override
	public void onCreate() {
		super.onCreate();
		sContext = getApplicationContext();
	}

	public static Context getAppContext() {
		if (sContext == null) {
			Log.e(TAG, "Global context not set");
		}
		return sContext;
	}


}
