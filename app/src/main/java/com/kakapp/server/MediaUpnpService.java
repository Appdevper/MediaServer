package com.kakapp.server;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.android.AndroidWifiSwitchableRouter;
import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.DIDLObject.Property;
import org.fourthline.cling.support.model.PersonWithRole;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.WriteStatus;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.ImageItem;
import org.fourthline.cling.support.model.item.MusicTrack;
import org.fourthline.cling.support.model.item.VideoItem;
import org.seamless.util.MimeType;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;

import com.kakapp.server.media.ContentNode;
import com.kakapp.server.media.ContentTree;
import com.kakapp.server.media.MediaServer;
import com.kakapp.server.util.Util;

public class MediaUpnpService extends AndroidUpnpServiceImpl {
	private static final String TAG = MediaUpnpService.class.getSimpleName();
	private static MediaServer mediaServer;
	static public final String ACTION_STARTED = "SERVER_STARTED";
	static public final String ACTION_STOPPED = "SERVER_STOPPED";
	static public final String ACTION_FAILEDTOSTART = "SERVER_FAILEDTOSTART";

	static public final String ACTION_START_SERVER = "ACTION_START_SERVER";
	static public final String ACTION_STOP_SERVER = "ACTION_STOP_SERVER";

	private static boolean b = false;

	protected AndroidUpnpServiceConfiguration createConfiguration(WifiManager wifiManager) {
		return new AndroidUpnpServiceConfiguration(wifiManager) {

		};
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		try {
			mediaServer = new MediaServer(getLocalInetAddress(), this, MediaSettings.getDeviceName());
			initMedia();
			prepareMediaServer();
		} catch (Exception e) {
			e.printStackTrace();
			mediaServer.stop();
			// sendBroadcast(new Intent(MediaUpnpService.ACTION_STOPPED));
			mediaServer = null;
		}

		if (mediaServer != null) {
			try {
				upnpService.getRegistry().addDevice(mediaServer.getDevice());
				sendBroadcast(new Intent(MediaUpnpService.ACTION_STARTED));
				b = true;
			} catch (Exception ex) {
				ex.printStackTrace();
				b = false;
				mediaServer.stop();
				sendBroadcast(new Intent(MediaUpnpService.ACTION_STOPPED));
			}
		} else {
			b = false;
			// mediaServer.stop();
			sendBroadcast(new Intent(MediaUpnpService.ACTION_STOPPED));
		}

		return START_STICKY;
	}

	public static boolean isRunning() {
		return b;
	}

	public static InetAddress getLocalInetAddress() {
		if (isConnectedToLocalNetwork() == false) {
			Log.e(TAG, "getLocalInetAddress called and no connection");
			return null;
		}
		// TODO: next if block could probably be removed
		if (isConnectedUsingWifi() == true) {
			Context context = AppController.getAppContext();
			WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			int ipAddress = wm.getConnectionInfo().getIpAddress();
			if (ipAddress == 0)
				return null;
			return Util.intToInet(ipAddress);
		}

		try {
			Enumeration<NetworkInterface> netinterfaces = NetworkInterface.getNetworkInterfaces();
			while (netinterfaces.hasMoreElements()) {
				NetworkInterface netinterface = netinterfaces.nextElement();
				Enumeration<InetAddress> adresses = netinterface.getInetAddresses();
				while (adresses.hasMoreElements()) {
					InetAddress address = adresses.nextElement();
					// this is the condition that sometimes gives problems
					if (address.isLoopbackAddress() == false && address.isLinkLocalAddress() == false)
						return address;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void onDestroy() {
		b = false;
		if (mediaServer != null)
			mediaServer.stop();
		sendBroadcast(new Intent(MediaUpnpService.ACTION_STOPPED));
		new Thread(new Runnable() {

			@Override
			public void run() {
				if (!ModelUtil.ANDROID_EMULATOR && isListeningForConnectivityChanges()) {
					unregisterReceiver(((AndroidWifiSwitchableRouter) upnpService.getRouter()).getBroadcastReceiver());
				}

				new Shutdown().execute(upnpService);

			}
		}).run();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		Log.d(TAG, "user has removed my activity, we got killed! restarting...");
		Intent restartService = new Intent(getApplicationContext(), this.getClass());
		restartService.setPackage(getPackageName());
		PendingIntent restartServicePI = PendingIntent.getService(getApplicationContext(), 1, restartService, PendingIntent.FLAG_ONE_SHOT);
		AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
		alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 2000, restartServicePI);
	}

	public static boolean isConnectedToLocalNetwork() {
		boolean connected = false;
		Context context = AppController.getAppContext();
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		connected = ni != null && ni.isConnected() == true && (ni.getType() & (ConnectivityManager.TYPE_WIFI | ConnectivityManager.TYPE_ETHERNET)) != 0;
		if (connected == false) {
			Log.d(TAG, "Device not connected to a network, see if it is an AP");
			WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			try {
				Method method = wm.getClass().getDeclaredMethod("isWifiApEnabled");
				connected = (Boolean) method.invoke(wm);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return connected;
	}

	public static boolean isConnectedUsingWifi() {
		Context context = AppController.getAppContext();
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		return ni != null && ni.isConnected() == true && ni.getType() == ConnectivityManager.TYPE_WIFI;
	}

	class Shutdown extends AsyncTask<UpnpService, Void, Void> {
		@Override
		protected Void doInBackground(UpnpService... svcs) {
			UpnpService svc = svcs[0];
			if (null != svc) {
				try {
					svc.shutdown();
				} catch (java.lang.IllegalArgumentException ex) {

					ex.printStackTrace();
				}
			}
			return null;
		}
	}

	private void initMedia() {
		UserData.getArVideo().clear();
		UserData.getArAudio().clear();
		UserData.getArImage().clear();

		if (MediaSettings.allowVideo()) {
			UserData.setArVideo(initVideo());
		}
		if (MediaSettings.allowAudio()) {
			UserData.setArAudio(initAudio());
		}
		if (MediaSettings.allowImage()) {
			UserData.setArImage(initImage());
		}
	}

	private void prepareMediaServer() {

		ContentNode rootNode = ContentTree.getRootNode();

		// Video Container
		Container videoContainer = new Container();
		videoContainer.setClazz(new DIDLObject.Class("object.container"));
		videoContainer.setId(ContentTree.VIDEO_ID);
		videoContainer.setParentID(ContentTree.ROOT_ID);
		videoContainer.setTitle("Videos");
		videoContainer.setChildCount(0);
		videoContainer.setCreator("MediaServer");
		videoContainer.setRestricted(true);
		videoContainer.setWriteStatus(WriteStatus.NOT_WRITABLE);

		rootNode.getContainer().addContainer(videoContainer);
		rootNode.getContainer().setChildCount(rootNode.getContainer().getChildCount() + 1);

		ContentTree.addNode(ContentTree.VIDEO_ID, new ContentNode(ContentTree.VIDEO_ID, videoContainer));

		ArrayList<VideoItem> arVideo = UserData.getArVideo();
		for (VideoItem videoItem : arVideo) {
			if (!UserData.getArSelectId().contains(videoItem.getId())) {
				videoContainer.addItem(videoItem);
				videoContainer.setChildCount(videoContainer.getChildCount() + 1);
				ContentTree.addNode(videoItem.getId(), new ContentNode(videoItem.getId(), videoItem, videoItem.getDescription()));

				Log.v(TAG, "added video item " + videoItem.getTitle() + "from " + videoItem.getDescription());
			}
		}

		// Audio Container
		Container audioContainer = new Container(ContentTree.AUDIO_ID, ContentTree.ROOT_ID, "Audios", "MediaServer", new DIDLObject.Class("object.container"), 0);
		audioContainer.setRestricted(true);
		audioContainer.setWriteStatus(WriteStatus.NOT_WRITABLE);

		rootNode.getContainer().addContainer(audioContainer);
		rootNode.getContainer().setChildCount(rootNode.getContainer().getChildCount() + 1);

		ContentTree.addNode(ContentTree.AUDIO_ID, new ContentNode(ContentTree.AUDIO_ID, audioContainer));

		ArrayList<MusicTrack> arAudio = UserData.getArAudio();
		for (MusicTrack musicTrack : arAudio) {
			if (!UserData.getArSelectId().contains(musicTrack.getId())) {
				audioContainer.addItem(musicTrack);
				audioContainer.setChildCount(audioContainer.getChildCount() + 1);
				ContentTree.addNode(musicTrack.getId(), new ContentNode(musicTrack.getId(), musicTrack, musicTrack.getDescription()));

				Log.v(TAG, "added audio item " + musicTrack.getTitle() + "from " + musicTrack.getDescription());
			}
		}

		// Image Container
		Container imageContainer = new Container(ContentTree.IMAGE_ID, ContentTree.ROOT_ID, "Images", "MediaServer", new DIDLObject.Class("object.container"), 0);
		imageContainer.setRestricted(true);
		imageContainer.setWriteStatus(WriteStatus.NOT_WRITABLE);

		rootNode.getContainer().addContainer(imageContainer);
		rootNode.getContainer().setChildCount(rootNode.getContainer().getChildCount() + 1);

		ContentTree.addNode(ContentTree.IMAGE_ID, new ContentNode(ContentTree.IMAGE_ID, imageContainer));

		ArrayList<ImageItem> arImage = UserData.getArImage();
		for (ImageItem imageItem : arImage) {
			if (!UserData.getArSelectId().contains(imageItem.getId())) {
				imageContainer.addItem(imageItem);
				imageContainer.setChildCount(imageContainer.getChildCount() + 1);
				ContentTree.addNode(imageItem.getId(), new ContentNode(imageItem.getId(), imageItem, imageItem.getDescription()));
				Log.v(TAG, "added image item " + imageItem.getTitle() + "from " + imageItem.getDescription());
			}
		}

	}

	private ArrayList<VideoItem> initVideo() {
		ArrayList<VideoItem> arItem = new ArrayList<VideoItem>();
		String[] videoColumns = { MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DATA, MediaStore.Video.Media.ARTIST, MediaStore.Video.Media.MIME_TYPE,
				MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.RESOLUTION };
		Cursor cursor = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoColumns, null, null, null);
		if (cursor.moveToFirst()) {
			do {
				String id = ContentTree.VIDEO_PREFIX + cursor.getInt(cursor.getColumnIndex(MediaStore.Video.Media._ID));
				String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE));
				String creator = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST));
				String filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
				String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE));
				long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE));
				long duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION));
				String resolution = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION));

				id = id + filePath.substring(filePath.lastIndexOf("."));

				Res res = new Res(new MimeType(mimeType.substring(0, mimeType.indexOf('/')), mimeType.substring(mimeType.indexOf('/') + 1)), size, "http://" + mediaServer.getAddress() + "/" + id);
				res.setDuration(duration / (1000 * 60 * 60) + ":" + cTime((duration % (1000 * 60 * 60)) / (1000 * 60)) + ":" + cTime((duration % (1000 * 60)) / 1000));
				res.setResolution(resolution);

				VideoItem videoItem = new VideoItem(id, ContentTree.VIDEO_ID, title, creator, res);
				videoItem.setDescription(filePath);
				arItem.add(videoItem);
				Log.v(TAG, "added video item " + title + "from " + filePath);
			} while (cursor.moveToNext());
		}
		return arItem;
	}

	private ArrayList<MusicTrack> initAudio() {
		ArrayList<MusicTrack> arItem = new ArrayList<MusicTrack>();
		String[] audioColumns = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.MIME_TYPE,
				MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM };
		Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioColumns, MediaStore.Audio.Media.DATA + " like ? ", new String[] { "%mp3" }, null);
		if (cursor.moveToFirst()) {
			do {
				String id = ContentTree.AUDIO_PREFIX + cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
				String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
				String creator = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
				String filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
				String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE));
				long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));
				long duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
				String album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));

				id = id + filePath.substring(filePath.lastIndexOf("."));

				Res res = new Res(new MimeType(mimeType.substring(0, mimeType.indexOf('/')), mimeType.substring(mimeType.indexOf('/') + 1)), size, "http://" + mediaServer.getAddress() + "/" + id);

				res.setDuration(duration / (1000 * 60 * 60) + ":" + cTime((duration % (1000 * 60 * 60)) / (1000 * 60)) + ":" + cTime((duration % (1000 * 60)) / 1000));

				MusicTrack musicTrack = new MusicTrack(id, ContentTree.AUDIO_ID, title, creator, album, new PersonWithRole(creator, "Performer"), res);
				musicTrack.setDescription(filePath);
				arItem.add(musicTrack);

				Log.v(TAG, "added audio item " + title + "from " + filePath);
			} while (cursor.moveToNext());
		}
		return arItem;
	}

	private ArrayList<ImageItem> initImage() {
		ArrayList<ImageItem> arItem = new ArrayList<ImageItem>();
		String[] imageColumns = { MediaStore.Images.Media._ID, MediaStore.Images.Media.TITLE, MediaStore.Images.Media.DATA, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.SIZE };
		Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageColumns, null, null, null);
		if (cursor.moveToFirst()) {
			do {
				String _id = "" + cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media._ID));
				String id = ContentTree.IMAGE_PREFIX + cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media._ID));
				String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.TITLE));
				String creator = "unkown";
				String filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
				String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE));
				long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE));

				id = id + filePath.substring(filePath.lastIndexOf("."));

				Res res = new Res(new MimeType(mimeType.substring(0, mimeType.indexOf('/')), mimeType.substring(mimeType.indexOf('/') + 1)), size, "http://" + mediaServer.getAddress() + "/" + id);
				@SuppressWarnings("rawtypes")
				Property albumArtURI = new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create("http://" + mediaServer.getAddress() + "/" + id));
				ImageItem imageItem = new ImageItem(id, ContentTree.IMAGE_ID, title, creator, res);
				imageItem.addProperty(albumArtURI);
				imageItem.setDescription(filePath);
				imageItem.setLongDescription(_id);
				arItem.add(imageItem);

				Log.v(TAG, "added image item " + title + "from " + filePath);
			} while (cursor.moveToNext());
		}
		return arItem;
	}

	private String cTime(long t) {
		String s = t + "";
		if (t < 10) {
			s = "0" + t;
		}
		return s;
	}

}