
package com.kakapp.server.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

import android.util.Log;

abstract public class Util {
    final static String TAG = Util.class.getSimpleName();

    public static byte byteOfInt(int value, int which) {
        int shift = which * 8;
        return (byte) (value >> shift);
    }

    public static String ipToString(int addr, String sep) {
        // myLog.l(Log.DEBUG, "IP as int: " + addr);
        if (addr > 0) {
            StringBuffer buf = new StringBuffer();
            buf.append(byteOfInt(addr, 0)).append(sep).append(byteOfInt(addr, 1))
                    .append(sep).append(byteOfInt(addr, 2)).append(sep)
                    .append(byteOfInt(addr, 3));
            Log.d(TAG, "ipToString returning: " + buf.toString());
            return buf.toString();
        } else {
            return null;
        }
    }

    public static InetAddress intToInet(int value) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = byteOfInt(value, i);
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            // This only happens if the byte array has a bad length
            return null;
        }
    }

}
