package com.njackson.strava;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.njackson.application.PebbleBikeApplication;
import com.njackson.pebble.IMessageManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import javax.inject.Inject;

import fr.jayps.android.AdvancedLocation;

public class StravaUpload {

    private static final String TAG = "PB-StravaUpload";

    @Inject IMessageManager _messageManager;
    @Inject SharedPreferences _sharedPreferences;
    Activity _activity = null;
    Context _context;

    public StravaUpload(Activity activity) {
        ((PebbleBikeApplication) activity.getApplicationContext()).inject(this);
        _activity = activity;
        _context = activity.getApplicationContext();
    }

    public StravaUpload(Context context) {
        ((PebbleBikeApplication) context).inject(this);
        _context = context;
    }

    public void upload(String token) {
        Toast.makeText(_context, "Strava: uploading... Please wait", Toast.LENGTH_LONG).show();
        final String strava_token = token;

        new Thread(new Runnable() {
            public void run() {
                String message = "";

                Log.i(TAG, "token: " + strava_token);

                Looper.prepare();
                AdvancedLocation advancedLocation = new AdvancedLocation(_context);
                String gpx = advancedLocation.getGPX(false);

                //String tmp_url = "http://labs.jayps.fr/pebble/strava.php";
                String tmp_url = "https://www.strava.com/api/v3/uploads";
                Log.d(TAG, "url="+tmp_url);

                // creates a unique boundary based on time stamp
                String boundary = "===" + System.currentTimeMillis() + "===";

                String lineEnd = "\r\n";
                String twoHyphens = "--";
                String data_type = "gpx";
                String external_id = "file" + (System.currentTimeMillis() / 1000);
                String description = "GPS track generated by Ventoo, http://www.pebblebike.com";

                try {
                    URL url = new URL(tmp_url);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                    urlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    String auth = "Bearer " + strava_token;
                    //Log.d(TAG, "auth="+auth);
                    urlConnection.setRequestProperty("Authorization", auth);
                    urlConnection.setDoOutput(true);

                    String data = new String();

                    data += twoHyphens + boundary + lineEnd;
                    // data_type
                    data += "Content-Disposition: form-data; name=\"data_type\"" + lineEnd;
                    data += "Content-Type: text/plain;charset=UTF-8" + lineEnd + "Content-Length: " + data_type.length() + lineEnd + lineEnd;
                    data += data_type + lineEnd + twoHyphens + boundary + lineEnd;

                    // external_id
                    /*data += "Content-Disposition: form-data; name=\"external_id\"" + lineEnd;
                    data += "Content-Type: text/plain;charset=UTF-8" + lineEnd + "Content-Length: " + external_id.length() + lineEnd + lineEnd;
                    data += external_id + lineEnd + twoHyphens + boundary + lineEnd;*/

                    // description
                    data += "Content-Disposition: form-data; name=\"description\"" + lineEnd;
                    data += "Content-Type: text/plain;charset=UTF-8" + lineEnd + "Content-Length: " + description.length() + lineEnd + lineEnd;
                    data += description + lineEnd + twoHyphens + boundary + lineEnd;

                    // gpx
                    data += "Content-Disposition: form-data; name=\"file\"; filename=\"" + external_id + ".gpx\"" + lineEnd;
                    data += "Content-Type: text/plain;charset=UTF-8" + lineEnd + "Content-Length: " + gpx.length() + lineEnd + lineEnd;
                    data += gpx + lineEnd + twoHyphens + boundary + twoHyphens + lineEnd;
                    // end

                    //Log.d(TAG, "data="+data);
                    //Log.d(TAG, "data.length()="+data.length());
                    urlConnection.setFixedLengthStreamingMode(data.length());

                    DataOutputStream outputStream = new DataOutputStream( urlConnection.getOutputStream() );
                    outputStream.writeBytes(data);

                    Log.d(TAG, "connection outputstream size is " + outputStream.size());

                    // finished with POST request body

                    outputStream.flush();
                    outputStream.close();

                    // checks server's status code first
                    // Responses from the server (code and message)
                    int serverResponseCode = urlConnection.getResponseCode();
                    String serverResponseMessage = urlConnection.getResponseMessage();

                    Log.d(TAG, "server response code: "+ serverResponseCode);
                    Log.d(TAG, "server response message: "+ serverResponseMessage);

                    message = serverResponseMessage + " (" + serverResponseCode + ")";

                    //start listening to the stream
                    String response = "";
                    // Strava doc: Upon a successful submission the request will return 201 Created. If there was an error the request will return 400 Bad Request.
                    InputStream is = null;
                    if (serverResponseCode == 201) {
                        is = urlConnection.getInputStream();
                        message = "Your activity has been successfully created";
                    } else if (serverResponseCode == 400) {
                        is = urlConnection.getErrorStream();
                        message = "An error has occurred. If you've alreaded uploaded the current activity, please delete it in Strava.";
                    } else if (serverResponseCode == 401) {
                        // {"message":"Authorization Error","errors":[{"resource":"Athlete","field":"access_token","code":"invalid"}]}
                        is = urlConnection.getErrorStream();
                        message = "Error - Unauthorized. Please check your credentials in the settings.";
                    }
                    if (is != null) {
                        Scanner inStream = new Scanner(is);
                        //process the stream and store it in StringBuilder
                        while (inStream.hasNextLine()) {
                            response += (inStream.nextLine()) + "\n";
                        }
                    }
                    Log.d(TAG, "response:" + response);
                    if (response != "") {
                        try {
                            JSONObject jObject = new JSONObject(response);
                            int strava_id = jObject.getInt("id");
                            String strava_status = jObject.getString("status");
                            String strava_activity_id = jObject.getString("activity_id");
                            Log.d(TAG, "strava_id:" + strava_id);
                            Log.d(TAG, "strava_status:" + strava_status);
                            Log.d(TAG, "strava_activity_id:" + strava_activity_id);
                            ///@todo save strava_id to later check status
                        } catch (JSONException e) {
                            Log.e(TAG, "Exception:" + e);
                        }
                    }
                    urlConnection.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Exception:" + e);
                    //Toast.makeText(_context, "Exception:" + e, Toast.LENGTH_LONG).show();
                    //message = "" + e;
                }
                final String _message = message;
                if (_activity != null) {
                    _activity.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(_activity.getApplicationContext(), "Strava: " + _message, Toast.LENGTH_LONG).show();
                            Log.d(TAG, "_message:" + _message);
                        }
                    });
                }
                if (_sharedPreferences.getBoolean("STRAVA_NOTIFICATION", false)) {
                    // use _messageManager and not _bus to be able to send data even if GPS is not started
                    _messageManager.sendMessageToPebble("Strava: " + _message);
                }
            }
        }).start();
    }
}