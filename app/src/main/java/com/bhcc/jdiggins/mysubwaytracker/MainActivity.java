package com.bhcc.jdiggins.mysubwaytracker;

/*

MyBusTracker CIT-243 Final Project
Monday, May 14, 2018
John Diggins

* Project implements JsonObject and JsonParser from GSON library

 */

import android.content.DialogInterface;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String API_KEY = "2cb9dd61049144159e15e4b94b67585b";
    private static final String BASE = "https://api-v3.mbta.com/";
    private static final String OAK_ID = "70029";
    private static final String FOREST_ID = "70028";
    private static final int DELAY = 20000;

    private static final int DEFAULT_THREAD_POOL_SIZE = 2;
    protected final int JOB_ID = 1;
    private ExecutorService mExecutor = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);

    public static AtomicReference<JsonObject> oakJson = new AtomicReference<>();
    public static AtomicReference<JsonObject> forestJson = new AtomicReference<>();

    public static AtomicReference<TrainPrediction> oakPrediction = new AtomicReference<>();
    public static AtomicReference<TrainPrediction> forestPrediction = new AtomicReference<>();

    public boolean hasBeenScheduled = false;
    Handler predictionHandler = new Handler();

    // runTask loops api calls - keeps times updated when user presses start button
    public Runnable runTask = new Runnable() {
        public void run() {
            initiatePredictions();
            predictionHandler.postDelayed(this, DELAY);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startButton = (Button) findViewById(R.id.btn_start);

        startButton.setOnClickListener(l-> {
            startTracking();
        });

        Button stopButton = (Button) findViewById(R.id.btn_stop);
        stopButton.setOnClickListener(l-> {
            stopTracking();
        });

        Button exitButton = (Button) findViewById(R.id.btn_exit);
        exitButton.setOnClickListener(l-> {
            launchExitConfirmMessage();
        });

        // copyright & api version
        TextView mCopyView = (TextView) findViewById(R.id.copyright_text);
        mCopyView.append(" API: ");
        mCopyView.append(Integer.toString(android.os.Build.VERSION.SDK_INT));
    }

    @MainThread
    private void displayOakInformation() {
        TextView nextOakText = (TextView) findViewById(R.id.text_next_oak);
        TextView secondOakText = (TextView) findViewById(R.id.text_second_oak);

        if(oakPrediction.get().getFirstPrediction() != null) {
            nextOakText.setText("Next train to Oak Grove: "
                    + oakPrediction.get().getFirstPrediction());
        } else {
            nextOakText.setText("Next train to Oak Grove: Not Found");
        }
        if(oakPrediction.get().getSecondPrediction() != null) {
            secondOakText.setText("2nd train to Oak Grove: "
                    + oakPrediction.get().getSecondPrediction());
        } else {
            secondOakText.setText("2nd train to Oak Grove: Not Found");
        }
    }

    @MainThread
    private void displayForestInformation() {
        TextView nextForestText = (TextView) findViewById(R.id.text_next_forest);
        TextView secondForestText = (TextView) findViewById(R.id.text_second_forest);

        if(forestPrediction.get().getFirstPrediction() != null) {
            nextForestText.setText("Next train to Forest Hills: "
                    + forestPrediction.get().getFirstPrediction());
        } else {
            nextForestText.setText("Next train to Forest Hills: Not Found");
        }

        if(forestPrediction.get().getSecondPrediction() != null) {
            secondForestText.setText("2nd train to Forest Hills: "
                    + forestPrediction.get().getSecondPrediction());
        } else {
            secondForestText.setText("2nd train to Forest Hills: Not Found");
        }

    }

    // Parse oak grove json data
    private TrainPrediction oakJsonToPrediction() {
        TrainPrediction oakPrediction = new TrainPrediction();
        try {
            JsonObject oakJSON = oakJson.get();
            JsonArray oakArray = null;
            if (oakJSON.has("data")) {
                oakArray = oakJSON.getAsJsonArray("data");

                // first oak grove prediction
                if(oakArray.size() > 0) {
                    if(oakArray.get(0).getAsJsonObject().has("attributes")) {
                        JsonObject attribs = oakArray.get(0).getAsJsonObject().getAsJsonObject("attributes");
                        if(attribs.has("arrival_time"))
                            oakPrediction.setFirstPrediction(
                                    parseTime(attribs.get("arrival_time").getAsString())
                                            + " minutes");

                    }

                    // second oak grove prediction
                    if(oakArray.size() > 1) {
                        if (oakArray.get(1).getAsJsonObject().has("attributes")) {
                            JsonObject attribs = oakArray.get(1).getAsJsonObject().getAsJsonObject("attributes");
                            if (attribs.has("arrival_time"))
                                oakPrediction.setSecondPrediction(
                                        parseTime(attribs.get("arrival_time").getAsString())
                                                + " minutes");
                        }
                    }
                }
            }
        } catch (JsonSyntaxException ex) {
            Log.e(TAG, "Failed to read oak prediction JSON", ex);
        }
        return oakPrediction;
    }

    // Parse forest hills Json data
    private TrainPrediction forestJsonToPrediction() {
        TrainPrediction forestPrediction = new TrainPrediction();

        try {
            JsonObject forestJSON = forestJson.get();
            JsonArray forestArray = null;
            if (forestJSON.has("data")) {
                forestArray = forestJSON.get("data").getAsJsonArray();

                // first forest hills prediction
                if(forestArray.size() >0) {
                    if(forestArray.get(0).getAsJsonObject().has("attributes")) {
                        JsonObject attribs = forestArray.get(0).getAsJsonObject().getAsJsonObject("attributes");
                        if(attribs.has("arrival_time"))
                            forestPrediction.setFirstPrediction(
                                    parseTime(attribs.get("arrival_time").getAsString())
                                            + " minutes");
                    }

                    // second forest hills prediction
                    if(forestArray.size() > 1) {
                        if (forestArray.get(1).getAsJsonObject().has("attributes")) {
                            JsonObject attribs = forestArray.get(1).getAsJsonObject().getAsJsonObject("attributes");
                            if (attribs.has("arrival_time"))
                                forestPrediction.setSecondPrediction(
                                        parseTime(attribs.get("arrival_time").getAsString())
                                                + " minutes");
                        }
                    }
                }
            }
        } catch (JsonSyntaxException ex) {
            Log.e(TAG, "Failed to read oak prediction JSON", ex);
        }
        return forestPrediction;
    }

    @NonNull
    private long parseTime(String time) {
        // time from json: 2018-04-27T19:00:20-04:00
        // SimpleDateFormat to hold time from MBTA Json
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        Date date = null;
        try {
            date = dateFormat.parse(time);
        } catch (ParseException ex) {
            Log.e("TAG", "Failed to parse time ", ex);
        }

        Date currentTime = Calendar.getInstance().getTime();

        long wait = -1;
        if(date != null) {
            // difference between current time and expected arrival time = wait time for train
            wait = (date.getTime() - currentTime.getTime()) / 1000 / 60;
            if(wait < 0)
                wait = 0;
            Log.i("TAG", "Minutes: " + wait);
        }
        return wait;
    }

    private URL buildPredictUri(String trainID) {
        Uri uri = Uri.
                parse(BASE)
                .buildUpon()
                .appendPath("predictions")
                .appendQueryParameter("filter[stop]", trainID)
                .appendQueryParameter("api_key", API_KEY)
                .build();
        URL endpoint = null;
        try {
            endpoint = new URL(uri.toString());
        } catch (MalformedURLException urlEx) {
            Log.e(TAG, "Failed to construct prediction URL", urlEx);
        }
        Log.i("TAG", "Url built: " + endpoint.toString());
        return endpoint;
    }

    private void getOakJson() {
        URL endpoint = buildPredictUri(OAK_ID);

        if(endpoint != null) {
            mExecutor.submit(() -> {
                try {
                    HttpURLConnection connection =
                            (HttpURLConnection) endpoint.openConnection();
                    ByteArrayOutputStream data = new ByteArrayOutputStream();
                    InputStream in = connection.getInputStream();
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        throw new IOException(connection.getResponseMessage() +
                                ": with " +
                                endpoint.toString());
                    }
                    int bytesRead = 0;
                    byte[] buffer = new byte[1024];
                    while((bytesRead = in.read(buffer)) > 0) {
                        data.write(buffer, 0, bytesRead);
                    }
                    data.close();

                    String response = data.toString();
                    JsonParser jsonParser = new JsonParser();
                    oakJson.set(jsonParser.parse(response).getAsJsonObject());
                    oakPrediction.set(oakJsonToPrediction());

                    Log.i(TAG, oakJson.get().toString());

                } catch (IOException ioEx) {
                    Log.e(TAG, "Network error when querying userinfo endpoint", ioEx);

                } catch (JsonParseException jsonEx) {
                    Log.e(TAG, "Failed to parse userinfo response");
                }
                runOnUiThread(this::displayOakInformation);
            });
        }
    }

    private void getForestJson() {
        URL endpoint = buildPredictUri(FOREST_ID);
        if(endpoint != null) {
            mExecutor.submit(() -> {
                try {
                    HttpURLConnection connection =
                            (HttpURLConnection) endpoint.openConnection();
                    ByteArrayOutputStream data = new ByteArrayOutputStream();
                    InputStream in = connection.getInputStream();
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        throw new IOException(connection.getResponseMessage() +
                                ": with " +
                                endpoint.toString());
                    }
                    int bytesRead = 0;
                    byte[] buffer = new byte[1024];
                    while((bytesRead = in.read(buffer)) > 0) {
                        data.write(buffer, 0, bytesRead);
                    }
                    data.close();

                    String response = data.toString();
                    JsonParser jsonParser = new JsonParser();
                    forestJson.set(jsonParser.parse(response).getAsJsonObject());
                    forestPrediction.set(forestJsonToPrediction());

                    Log.i(TAG, forestJson.get().toString());

                } catch (IOException ioEx) {
                    Log.e(TAG, "Network error when querying userinfo endpoint", ioEx);

                } catch (JsonParseException jsonEx) {
                    Log.e(TAG, "Failed to parse userinfo response");
                }
                runOnUiThread(this::displayForestInformation);
            });
        }
    }

    public void initiatePredictions() {
        getOakJson();
        getForestJson();
    }

    private void startTracking() {
        if(!hasBeenScheduled) {
            initiatePredictions();
            predictionHandler.postDelayed(runTask
                    , DELAY);
            hasBeenScheduled = true;
        }
    }

    private void stopTracking() {
        if(hasBeenScheduled) {
            predictionHandler.removeCallbacks(runTask);
            hasBeenScheduled = false;
        }
    }

    private void launchExitConfirmMessage() {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        // exit app
                        finish();
                        System.exit(0);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        // cancel
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure you want to exit?").setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
    }

    public class TrainPrediction {
        private String firstPrediction;
        private String secondPrediction;

        public String getFirstPrediction() {
            return firstPrediction;
        }

        public void setFirstPrediction(String firstPrediction) {
            this.firstPrediction = firstPrediction;
        }

        public String getSecondPrediction() {
            return secondPrediction;
        }

        public void setSecondPrediction(String secondPrediction) {
            this.secondPrediction = secondPrediction;
        }
    }

}
