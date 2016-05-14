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

import com.afollestad.materialdialogs.MaterialDialog;
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
    private MaterialDialog loadingDialog;

    private int currentSelectedActivity = -1;

    private boolean currentlyRecording = false;

    private  final int recordMessage = 1;
    private  final int stopMessage = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Grid View Setup
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

        // Record Button Setup
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

        // Progress Loading Dialog
        loadingDialog = new MaterialDialog.Builder(this)
                .title(R.string.collecting_log_header)
                .content(R.string.collecting_log_content)
                .progress(true, 0)
                .progressIndeterminateStyle(true)
                .canceledOnTouchOutside(false)
                .build();

        PebbleKit.registerReceivedAckHandler(getApplicationContext(), new PebbleKit.PebbleAckReceiver(APP_UUID) {
            @Override
            public void receiveAck(Context context, int transactionId) {
                if (transactionId == recordMessage) {
                    Toast.makeText(getApplicationContext(), "Recording has started", Toast.LENGTH_SHORT).show();
                    recordBtn.setImageResource(R.drawable.stop);
                    recordBtn.setAlpha((float)1);
                } else if (transactionId == stopMessage){
                    Toast.makeText(getApplicationContext(), "Recording has Stopped", Toast.LENGTH_SHORT).show();
                    recordBtn.setImageResource(R.drawable.record);
                    recordBtn.setAlpha((float)1);
                    loadingDialog.show();
                }
            }
        });

        PebbleKit.registerReceivedNackHandler(getApplicationContext(), new PebbleKit.PebbleNackReceiver(APP_UUID) {
            @Override
            public void receiveNack(Context context, int transactionId) {
                if (transactionId == stopMessage) {
                    Toast.makeText(getApplicationContext(), "Failed to stop Recording, Try Again or Press Back Button on your Pebble", Toast.LENGTH_SHORT).show();
                    recordBtn.setAlpha((float)1);
                } else if (transactionId == recordMessage){
                    Toast.makeText(getApplicationContext(), "Failed to start Recording, Try Again. Try launching the Watchapp on Pebble", Toast.LENGTH_SHORT).show();
                    recordBtn.setAlpha((float)1);
                    currentlyRecording = false;
                }
            }
        });

        dataLogReceiver = new PebbleKit.PebbleDataLogReceiver(APP_UUID) {

            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                // Individual Data batches are collected here and written to CSV
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
                // Open the CSV file and send Email once data logging session is complete
                Log.i("Pebble-Phase-1", "Session " + tag + " finished!");
                loadingDialog.dismiss();
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
    }

    private boolean isPebbleConnected() {
        return PebbleKit.isWatchConnected(this);
    }

    private void startRecording() {
        try {
            csvFile = new File(Environment.getExternalStorageDirectory(), activities[currentSelectedActivity] + System.currentTimeMillis() + ".csv");
            writer = new CSVWriter(new FileWriter(csvFile));
        } catch (Exception e) {
            Log.e("Pebble-Phase-1", "Unable to create CSV Writer or use FileWriter");
            Toast.makeText(getApplicationContext(), "Unable to write to External Storage", Toast.LENGTH_SHORT).show();
            currentlyRecording = false;
            return;
        }

        // Write the header of CSV File
        String activity = activities[currentSelectedActivity];
        writer.writeNext(new String[]{"Activity is " + activity});
        writer.writeNext(new String[]{"x","y","z","timestamp"});

        recordBtn.setAlpha((float) 0.5);

        PebbleDictionary dict = new PebbleDictionary();

        final int AppKeySessionStatus = 0;
        final int AppKeyActivityType = 1;
        dict.addInt32(AppKeySessionStatus, 1);
        dict.addInt32(AppKeyActivityType, currentSelectedActivity);

        PebbleKit.sendDataToPebbleWithTransactionId(this, APP_UUID, dict, recordMessage);
    }

    private void stopRecording() {
        PebbleDictionary dict = new PebbleDictionary();

        final int AppKeySessionStatus = 0;
        dict.addInt32(AppKeySessionStatus, 2);

        PebbleKit.sendDataToPebbleWithTransactionId(this, APP_UUID, dict, stopMessage);

        recordBtn.setAlpha((float) 0.5);
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
        PebbleKit.startAppOnPebble(getApplicationContext(), APP_UUID);

        PebbleKit.registerDataLogReceiver(getApplicationContext(), dataLogReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            unregisterReceiver(dataLogReceiver);
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
