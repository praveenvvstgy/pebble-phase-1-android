package com.praveengowda.app.phase1;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    final UUID APP_UUID = UUID.fromString("aac15e6a-ea91-4059-b5eb-49824a210c07");
    final String[] activities = {"Running", "Walking", "Sitting", "Sleeping", "Standing", "Driving"};

    PebbleKit.PebbleDataLogReceiver dataLogReceiver;
    PebbleKit.PebbleDataReceiver dataReceiver;

    CSVWriter writer;

    File csvFile;

    private GridView gridView;
    private GridViewAdapter gridAdapter;
    private ImageView recordBtn;

    private int currentSelectedActivity = -1;

    private boolean currentlyRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gridView = (GridView) findViewById(R.id.grid_view);
        gridAdapter = new GridViewAdapter(this, R.layout.activity_cell_card, getData());
        gridView.setAdapter(gridAdapter);

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                gridView.setItemChecked(position, true);
                currentSelectedActivity = position;
            }
        });

        recordBtn = (ImageView) findViewById(R.id.record_btn);
        recordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPebbleConnected()) {
                    if (!currentlyRecording) {
                        if (currentSelectedActivity != -1) {
                            startRecording();
                            currentlyRecording = true;
                        } else {
                            Toast.makeText(getApplicationContext(), "Please select an Activity", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        currentlyRecording = false;
                        stopRecording();
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Your Pebble is not connected", Toast.LENGTH_SHORT).show();
                }
            }
        });

        dataLogReceiver = new PebbleKit.PebbleDataLogReceiver(APP_UUID) {
            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, int data) {
                Log.i("Pebble-Phase-1", "New data for session " + tag + "!");
            }

            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                Log.i("Pebble-Phase-1", "New data for session " + tag + "!");
                
                int x = (int)decodeBytes(new byte[]{data[0], data[1]});
                int y = (int)decodeBytes(new byte[]{data[2], data[3]});
                int z = (int)decodeBytes(new byte[]{data[4], data[5]});
                long ts = (long)decodeBytes(new byte[]{data[6], data[7], data[8], data[9], data[10], data[11]});
                String[] readings = {Integer.toString(x), Integer.toString(y), Integer.toString(z), Long.toString(ts)};
                writer.writeNext(readings);
                Log.v("ACCEL_READING", ts + ": x = " + x + ",y = " + y + ", z = " + z + "\n");
            }

            @Override
            public void onFinishSession(Context context, UUID logUuid, Long timestamp, Long tag) {
                Log.i("Pebble-Phase-1", "Session " + tag + " finished!");
                try {
                    writer.close();
                    Intent i = new Intent();
                    i.setAction(android.content.Intent.ACTION_VIEW);
                    i.setDataAndType(Uri.fromFile(csvFile), "text/csv");
                    startActivity(i);
                    Intent emailIntent = new Intent();
                    emailIntent.setType("vnd.android.cursor.dir/email");
                    emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(csvFile));
                    startActivity(emailIntent.createChooser(emailIntent, "Send Email..."));
                } catch (IOException e) {
                    Log.e("Pebble-Phase-1", "Closing writer caused an error");
                }
            }
        };

        dataReceiver = new PebbleKit.PebbleDataReceiver(APP_UUID) {
            @Override
            public void receiveData(Context context, int transactionId, PebbleDictionary data) {
                final int AppKeySessionStatus = 0;
                final int AppKeyActivityType = 1;

                Long statusValue = data.getInteger(AppKeySessionStatus);
                if (statusValue != null) {
                    switch (statusValue.intValue()) {
                        case 1:
                            Toast.makeText(getApplicationContext(), "Pebble has started recording", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                    }
                }

                Long activityType = data.getInteger(AppKeyActivityType);
                if (activityType != null) {
                    String activity = activities[activityType.intValue()];
                    writer.writeNext(new String[]{"Activity is " + activity});
                    writer.writeNext(new String[]{"x","y","z","timestamp"});
                }
                PebbleKit.sendAckToPebble(context, transactionId);
            }
        };
    }

    private boolean isPebbleConnected() {
        return PebbleKit.isWatchConnected(this);
    }

    private void startRecording() {
        try {
            csvFile = new File(Environment.getExternalStorageDirectory(), UUID.randomUUID().toString() + ".csv");
            writer = new CSVWriter(new FileWriter(csvFile));
        } catch (Exception e) {
            Log.e("Pebble-Phase-1", "Unable to create CSV Writer or use FileWriter");
        }
        String activity = activities[currentSelectedActivity];
        writer.writeNext(new String[]{"Activity is " + activity});
        writer.writeNext(new String[]{"x","y","z","timestamp"});
        PebbleKit.startAppOnPebble(getApplicationContext(), APP_UUID);
        PebbleDictionary dict = new PebbleDictionary();

        final int AppKeySessionStatus = 0;
        final int AppKeyActivityType = 1;
        dict.addInt32(AppKeySessionStatus, 1);
        dict.addInt32(AppKeyActivityType, currentSelectedActivity);

        PebbleKit.sendDataToPebble(this, APP_UUID, dict);
        Toast.makeText(this, "Sending Request to Device to Start Recording", Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        PebbleDictionary dict = new PebbleDictionary();

        final int AppKeySessionStatus = 0;
        dict.addInt32(AppKeySessionStatus, 2);

        PebbleKit.sendDataToPebble(this, APP_UUID, dict);
        Toast.makeText(this, "Recording Stopped", Toast.LENGTH_SHORT).show();
    }

    private ArrayList<ImageItem> getData() {
        final ArrayList<ImageItem> imageItems = new ArrayList<>();
        TypedArray imgs = getResources().obtainTypedArray(R.array.activity_images);
        TypedArray names = getResources().obtainTypedArray(R.array.activity_names);
        for (int i = 0; i < imgs.length(); i++) {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), imgs.getResourceId(i, -1));
            imageItems.add(new ImageItem(bitmap, names.getString(i)));
        }
        return imageItems;
    }

    @Override
    protected void onResume() {
        super.onResume();

        PebbleKit.registerDataLogReceiver(getApplicationContext(), dataLogReceiver);
        PebbleKit.registerReceivedDataHandler(getApplicationContext(), dataReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            unregisterReceiver(dataLogReceiver);
            unregisterReceiver(dataReceiver);
        } catch (Exception e) {
            Log.w("Pebble-Phase-1", "Receiver did not need to be unregistered");
        }
    }

    private long decodeBytes(byte[] bytes) {
        /* Note on Java and Bitwise Operators
         *  Java bitwise operators only work on ints and longs,
         *  Bytes will undergo promotion with sign extension first.
         *  So, we have to undo the sign extension on the lower order
         *  bits here.
         */
        long ans = bytes[0];
        for (int i = 1; i < bytes.length; i++) {
            ans <<= 8;
            ans |= bytes[i] & 0x000000FF;
        }
        return ans;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }
}
