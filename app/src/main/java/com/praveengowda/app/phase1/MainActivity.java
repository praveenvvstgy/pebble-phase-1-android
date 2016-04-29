package com.praveengowda.app.phase1;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    final UUID APP_UUID = UUID.fromString("aac15e6a-ea91-4059-b5eb-49824a210c07");
    final String[] activities = {"Sitting", "Walking", "Running", "Jumping"};

    PebbleKit.PebbleDataLogReceiver dataLogReceiver;
    PebbleKit.PebbleDataReceiver dataReceiver;

    CSVWriter writer;

    File csvFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dataLogReceiver = new PebbleKit.PebbleDataLogReceiver(APP_UUID) {
            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, int data) {
                Log.i("Pebble-Phase-1", "New data for session " + tag + "!");
            }

            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                Log.i("Pebble-Phase-1", "New data for session " + tag + "!");
                TextView dataView = (TextView)findViewById(R.id.text_view);
                String current = dataView.getText().toString();
                
                int x = (int)decodeBytes(new byte[]{data[0], data[1]});
                int y = (int)decodeBytes(new byte[]{data[2], data[3]});
                int z = (int)decodeBytes(new byte[]{data[4], data[5]});
                long ts = (long)decodeBytes(new byte[]{data[6], data[7], data[8], data[9], data[10], data[11]});
                String[] readings = {Integer.toString(x), Integer.toString(y), Integer.toString(z), Long.toString(ts)};
                writer.writeNext(readings);
                Log.v("ACCEL_READING", ts + ": x = " + x + ",y = " + y + ", z = " + z + "\n");
                dataView.setText(current);
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

                TextView dataView = (TextView)findViewById(R.id.text_view);
                String current = dataView.getText().toString();

                Long statusValue = data.getInteger(AppKeySessionStatus);
                if (statusValue != null) {
                    switch (statusValue.intValue()) {
                        case 1:
                            current += "Activity Recording Started.\n";
                            try {
                                csvFile = new File(Environment.getExternalStorageDirectory(), UUID.randomUUID().toString() + ".csv");
                                writer = new CSVWriter(new FileWriter(csvFile));
                            } catch (Exception e) {
                                Log.e("Pebble-Phase-1", "Unable to create CSV WRIter or use FileWriter");
                            }

                            break;
                        case 2:
                            current += "Activity Recording Stopped.\n";
                    }
                }

                Long activityType = data.getInteger(AppKeyActivityType);
                if (activityType != null) {
                    String activity = activities[activityType.intValue()];
                    current += "Activity is " + activity + ".\n";
                    writer.writeNext(new String[]{"Activity is " + activity});
                    writer.writeNext(new String[]{"x","y","z","timestamp"});
                }
                dataView.setText(current);
                PebbleKit.sendAckToPebble(context, transactionId);
            }
        };
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
}
