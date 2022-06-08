package com.sample.sik_ligtas_proto.ShakeServices;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;



import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnTokenCanceledListener;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sample.sik_ligtas_proto.Contacts.ContactModel;
import com.sample.sik_ligtas_proto.Contacts.DbHelper;
import com.sample.sik_ligtas_proto.R;


import java.util.List;

public class SensorService extends Service {

    Context context;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;

    public SensorService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        return START_STICKY;
    }

    @Override
    public void onCreate() {

        super.onCreate();

        // start the foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startMyOwnForeground();
        else
            startForeground(1, new Notification());

        // ShakeDetector initialization
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mShakeDetector = new ShakeDetector();
        mShakeDetector.setOnShakeListener(new ShakeDetector.OnShakeListener() {

            @SuppressLint("MissingPermission")
            @Override
            public void onShake(int count) {
                // check if the user has shook
                // the phone for 3 time in a row
                if (count == 3) {

                    vibrate();
                    AlertMe();

                }
            }
        });

        // register the listener
        mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    private void AlertMe() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(SensorService.this);
        alertDialogBuilder.setTitle("We detected an unexpected collision");
        alertDialogBuilder.setMessage("Do you need medical assistance? If you don't respond within 2 minutes, I will notify everyone on your emergency contacts.");
        alertDialogBuilder.setPositiveButton("YES", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Toast.makeText(SensorService.this, "Requesting Emergency Services", Toast.LENGTH_SHORT).show();
                    CallServices();
                }
            });
        alertDialogBuilder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Toast.makeText(SensorService.this, "Request for Emergency Services Cancelled", Toast.LENGTH_SHORT).show();
                }
            });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialogBuilder.show();
    }

    private void CallServices() {
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, new CancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return false;
            }

            @NonNull
            @Override
            public CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener onTokenCanceledListener) {
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    SmsManager smsManager = SmsManager.getDefault();
                    DbHelper db = new DbHelper(SensorService.this);
                    List<ContactModel> list = db.getAllContacts();

                    for (ContactModel c : list) {
                        String message = "Hey " + c.getName() + ", I am in DANGER, I need help. Please urgently reach me out. Here are my coordinates.\n " + "http://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
                        smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                    }
                } else {
                    String message = "I am in DANGER, I need help. Please urgently reach me out.\n" + "GPS was turned off. Couldn't find location. Call your nearest Police Station.";
                    SmsManager smsManager = SmsManager.getDefault();
                    DbHelper db = new DbHelper(SensorService.this);
                    List<ContactModel> list = db.getAllContacts();
                    for (ContactModel c : list) {
                        smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                    }
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("Check: ", "OnFailure");
                String message = "I am in DANGER, I need help. Please urgently reach me out.\n" + "GPS was turned off. Couldn't find location. Call your nearest Police Station.";
                SmsManager smsManager = SmsManager.getDefault();
                DbHelper db = new DbHelper(SensorService.this);
                List<ContactModel> list = db.getAllContacts();
                for (ContactModel c : list) {
                    smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                }
            }
        });
    }


    // method to vibrate the phone
    public void vibrate() {

        final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        VibrationEffect vibEff;

        // Android Q and above have some predefined vibrating patterns
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibEff = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK);
            vibrator.cancel();
            vibrator.vibrate(vibEff);
        } else {
            vibrator.vibrate(5000);
        }


    }
    @RequiresApi(Build.VERSION_CODES.O)

    private void startMyOwnForeground() {
        String NOTIFICATION_CHANNEL_ID = "example.permanence";
        String channelName = "Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_MIN);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("You are now protected")
                .setContentText("Any unexpected collisions will be sent to emergency contacts.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }

    @Override
    public void onDestroy() {
        // create an Intent to call the Broadcast receiver
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, ReactivateService.class);
        this.sendBroadcast(broadcastIntent);
        super.onDestroy();
    }

}

