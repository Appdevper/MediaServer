package com.kakapp.server.media;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;

import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.ManufacturerDetails;
import org.fourthline.cling.model.meta.ModelDetails;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import com.kakapp.server.R;

public class MediaServer {

	private UDN udn = UDN.uniqueSystemIdentifier("Kak-MediaServer");
	private LocalDevice localDevice;

	private final static String deviceType = "MediaServer";
	private final static int version = 1;
	private final static String LOGTAG = "iKak-MediaServer";
	private final static int port = 8191;
	private InetAddress localAddress;
	private Context c;
	private HttpServer hServer;

	@SuppressWarnings("unchecked")
	public MediaServer(InetAddress localAddress, Context context , String name) throws ValidationException {
		this.c = context;
		DeviceType type = new UDADeviceType(deviceType, version);

		DeviceDetails details = new DeviceDetails(name, new ManufacturerDetails(android.os.Build.MANUFACTURER), new ModelDetails("iKak", "MediaServer Android", "v1"));

		LocalService<ContentDirectoryService> service = new AnnotationLocalServiceBinder().read(ContentDirectoryService.class);
		service.setManager(new DefaultServiceManager<ContentDirectoryService>(service, ContentDirectoryService.class));
		Icon[] ic = new Icon[] { createDefaultDeviceIcon() };
		localDevice = new LocalDevice(new DeviceIdentity(udn), type, details, ic, service);

		this.localAddress = localAddress;

		Log.v(LOGTAG, "MediaServer device created: ");
		Log.v(LOGTAG, "friendly name: " + details.getFriendlyName());
		Log.v(LOGTAG, "manufacturer: " + details.getManufacturerDetails().getManufacturer());
		Log.v(LOGTAG, "model: " + details.getModelDetails().getModelName());

		// start http server
		try {
			hServer = new HttpServer(port);
		} catch (IOException ioe) {
			System.err.println("Couldn't start server:\n" + ioe);
			System.exit(-1);
		}

		Log.v(LOGTAG, "Started Http Server on port " + port);
	}

	public LocalDevice getDevice() {
		return localDevice;
	}

	public String getAddress() {
		return localAddress.getHostAddress() + ":" + port;
	}

	public void stop() {
		hServer.stop();
	}

	protected Icon createDefaultDeviceIcon() {

		BitmapDrawable bitDw = ((BitmapDrawable) c.getResources().getDrawable(R.drawable.ic_launcher));
		Bitmap bitmap = bitDw.getBitmap();
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
		byte[] imageInByte = stream.toByteArray();
		System.out.println("........length......" + imageInByte);
		ByteArrayInputStream bis = new ByteArrayInputStream(imageInByte);

		try {
			return new Icon("image/png", 120, 120, 8, URI.create("icon.png"), bis);
		} catch (IOException ex) {
			throw new RuntimeException("Could not load icon", ex);
		}
	}

}
