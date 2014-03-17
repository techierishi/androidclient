package org.kontalk.client;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;

import android.util.Log;


public class HttpDownload extends Thread {

    private String mUrl;
    private File mFile;
    private Runnable mSuccess;
    private Runnable mError;

    public HttpDownload (String url, File destination, Runnable success, Runnable error) {
        this.mUrl=url;
        this.mFile = destination;
        this.mSuccess=success;
        this.mError=error;
    }

    public void run() {
        HttpClient client = new DefaultHttpClient();
        HttpRequestBase req=new HttpGet(mUrl);
        try {
            HttpResponse resp=client.execute(req);
            if (resp.getStatusLine().getStatusCode()==200) {
                FileOutputStream out = new FileOutputStream(mFile);
                resp.getEntity().writeTo(out);
                out.close();
                resp.getEntity().consumeContent();

                mSuccess.run();
            }

            else {
                mError.run();
            }
        }
        catch (Exception e)
            {
            Log.e("Kontalk","Error Downloading File",e);
            mError.run();
        }

    }

}
