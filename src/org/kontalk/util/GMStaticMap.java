package org.kontalk.util;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.kontalk.client.HttpDownload;

import android.content.Context;


public class GMStaticMap {

    private static final String GM_MAP_TYPE = "roadmap";
    private static final String GM_MARKER_COLOR = "red";
    private static final char GM_MARKER_LABEL = '\0';
    private static final boolean GM_SENSOR = false;
    private static final int GM_MAP_WIDTH = 600;
    private static final int GM_MAP_HEIGHT = 300;
    private static final int GM_MAP_ZOOM = 13;

    private static GMStaticMap sInstance;

    public static GMStaticMap getInstance(Context context) {
        if (sInstance == null)
            sInstance = new GMStaticMap(context.getApplicationContext());

        return sInstance;
    }

    private final Context mContext;

    private final Map<String, Set<GMStaticMapListener>> mQueue;

    private GMStaticMap(Context context) {
        mContext = context;
        mQueue = new HashMap<String, Set<GMStaticMapListener>>();
    }

    public File requestMap(double lat, double lon, GMStaticMapListener listener) {
        String filename = String.format(Locale.US, "gmap_%f_%f.png", lat, lon);
        final File dest = new File(mContext.getCacheDir(), filename);

        // file exists - return file object
        if (dest.isFile())
            return dest;

        GMStaticUrlBuilder url = new GMStaticUrlBuilder()
            .setCenter(lat, lon)
            .setMapType(GM_MAP_TYPE)
            .setMarker(GM_MARKER_COLOR, GM_MARKER_LABEL, lat, lon)
            .setSensor(GM_SENSOR)
            .setSize(GM_MAP_WIDTH, GM_MAP_HEIGHT)
            .setZoom(GM_MAP_ZOOM);

        // queue the download
        queueDownload(url.toString(), filename, dest, listener);

        return null;
    }

    private void queueDownload(String url, final String id, final File destination,
            GMStaticMapListener listener) {

        boolean start = false;

        synchronized (mQueue) {
            Set<GMStaticMapListener> listeners = mQueue.get(id);
            if (listeners == null) {
                listeners = new LinkedHashSet<GMStaticMapListener>(1);
                mQueue.put(id, listeners);

                start = true;
            }

            listeners.add(listener);
        }

        if (start)
            new HttpDownload(url, destination,
                new Runnable() {
                    public void run() {
                        completed(id, destination);
                    }
                },
                new Runnable() {
                    public void run() {
                        error(id, destination);
                    }
                })
            .start();
    }

    public void completed(String id, File destination) {
        // TODO check file size

        synchronized (mQueue) {
            Set<GMStaticMapListener> listeners = mQueue.get(id);
            if (listeners != null) {
                for (GMStaticMapListener l : listeners)
                    l.completed(destination);

                mQueue.remove(id);
            }
        }
    }

    public void error(String id, File destination) {
        // delete file
        destination.delete();

        synchronized (mQueue) {
            Set<GMStaticMapListener> listeners = mQueue.get(id);
            if (listeners != null) {
                for (GMStaticMapListener l : listeners)
                    l.error(destination);

                mQueue.remove(id);
            }
        }
    }

    public interface GMStaticMapListener {
        public void completed(File destination);
        public void error(File destination);
    }

}
