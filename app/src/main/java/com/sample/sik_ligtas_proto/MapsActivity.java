package com.sample.sik_ligtas_proto;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnTokenCanceledListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sample.sik_ligtas_proto.Contacts.ContactModel;
import com.sample.sik_ligtas_proto.Contacts.DbHelper;
import com.sample.sik_ligtas_proto.DirectionHelpers.FetchURL;
import com.sample.sik_ligtas_proto.DirectionHelpers.TaskLoadedCallback;
import com.sample.sik_ligtas_proto.ShakeServices.SensorService;
import com.sample.sik_ligtas_proto.ShakeServices.ShakeDetector;
import com.sample.sik_ligtas_proto.databinding.ActivityMapsBinding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, TaskLoadedCallback{

    private double latitude, longitude;
    private GoogleMap mMap;
    private MarkerOptions place1, place2;
    private Polyline currentPolyline;
    private AlertDialog alertDialog;
    private long TimerLeftInMillis = Start_time_millis;
    final static long Start_time_millis = 120000;
    CountDownTimer timer;


    List<MarkerOptions> markerOptionsList = new ArrayList<>();
    AppCompatButton menuBtn;
    TextView day_manager, curr_location, userName, nav_title;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;
    String userId;
    Button LocationButton;
    FusedLocationProviderClient fusedLocationProviderClient;
    SupportMapFragment mapFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SensorManager mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        ShakeDetector mShakeDetector = new ShakeDetector();
        mShakeDetector.setOnShakeListener(count -> {
            // check if the user has shook
            // the phone for 3 time in a row
            if (count == 3) {
                AlertMe();
            }
        });

        // register the listener
        mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);

        // start the service
        SensorService sensorService = new SensorService();
        Intent intent = new Intent(this, sensorService.getClass());
        if (!isMyServiceRunning(sensorService.getClass())) {
            startService(intent);
        }

        com.sample.sik_ligtas_proto.databinding.ActivityMapsBinding binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        curr_location = findViewById(R.id.curr_location);
        day_manager = findViewById(R.id.day_manager);
        menuBtn = findViewById(R.id.menuBtn);
        userName = findViewById(R.id.userName);
        nav_title = findViewById(R.id.nav_title);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        if (ActivityCompat.checkSelfPermission(MapsActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getLocation();
        } else {
            ActivityCompat.requestPermissions(MapsActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,}, 44);
        }

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        userId = Objects.requireNonNull(fAuth.getCurrentUser()).getUid();

        DocumentReference documentReference = fStore.collection("users").document(userId);
        documentReference.addSnapshotListener(this, (value, error) -> {
            assert value != null;
            userName.setText(value.getString("fName"));
            userName.setTextSize(40);
        });

        menuBtn.setOnClickListener(v -> go_to_menu());

        myDay();

        LocationButton = findViewById(R.id.LocationButton);
        LocationButton.setOnClickListener(view -> new FetchURL(MapsActivity.this)
                .execute(getUrl(place1.getPosition(), place2.getPosition()), "driving"));

        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        getCurrLocation();
    }

    private void timer(){
        timer = new CountDownTimer(TimerLeftInMillis, 1000){
            @Override
            public void onTick(long millisuntilfinish){
                TimerLeftInMillis=millisuntilfinish;
            }
            @Override
            public void onFinish() {
                CallServices();
            }
        };
    }

    private void AlertMe() {
        TimerLeftInMillis = Start_time_millis;
        timer();
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MapsActivity.this);
        alertDialogBuilder.setTitle("We detected an unexpected collision");
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setMessage("Do you need medical assistance? If you don't respond within 2 minutes, I will notify everyone on your emergency contacts.");
        alertDialogBuilder.setPositiveButton("YES", (dialog, which) -> {
            Toast.makeText(MapsActivity.this, "Requesting Emergency Services", Toast.LENGTH_SHORT).show();
            CallServices();
        });
        alertDialogBuilder.setNegativeButton("CANCEL", (dialog, which) -> Toast.makeText(MapsActivity.this, "Request for Emergency Services Cancelled", Toast.LENGTH_SHORT).show());
        alertDialogBuilder.create();
        alertDialogBuilder.show();
    }

    public void CallServices() {
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
        }).addOnSuccessListener(location -> {
            if (location != null) {
                SmsManager smsManager = SmsManager.getDefault();
                DbHelper db = new DbHelper(MapsActivity.this);
                List<ContactModel> list = db.getAllContacts();

                for (ContactModel c : list) {
                    String message = "Hey " + c.getName() + ", I am in DANGER, I need help. Please urgently reach me out. Here are my coordinates.\n " + "http://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
                    smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                }
            } else {
                String message = "I am in DANGER, I need help. Please urgently reach me out.\n" + "GPS was turned off. Couldn't find location. Call your nearest Police Station.";
                SmsManager smsManager = SmsManager.getDefault();
                DbHelper db = new DbHelper(MapsActivity.this);
                List<ContactModel> list = db.getAllContacts();
                for (ContactModel c : list) {
                    smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                }
            }
        }).addOnFailureListener(e -> {
            Log.d("Check: ", "OnFailure");
            String message = "I am in DANGER, I need help. Please urgently reach me out.\n" + "GPS was turned off. Couldn't find location. Call your nearest Police Station.";
            SmsManager smsManager = SmsManager.getDefault();
            DbHelper db = new DbHelper(MapsActivity.this);
            List<ContactModel> list = db.getAllContacts();
            for (ContactModel c : list) {
                smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
            }
        });
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.i("Service status", "Running");
                return true;
            }
        }
        Log.i("Service status", "Not running");
        return false;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
    }

    private void setMarkers(){
        place1 = new MarkerOptions().position(new LatLng(latitude, longitude)).title("You are Here");
        place2 = new MarkerOptions().position(new LatLng(14.9447777, 120.8899436)).title("Need Urgent Assistance");

        markerOptionsList.add(place1);
        markerOptionsList.add(place2);

        mMap.addMarker(place1);
        mMap.addMarker(place2);
        showAllMarkers();
    }

    private void showAllMarkers() {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (MarkerOptions m : markerOptionsList) {
            builder.include(m.getPosition());
        }
        LatLngBounds bounds = builder.build();

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int padding = (int) (width * 0.30);

        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding);
        mMap.animateCamera(cu);

    }

    private String getUrl(LatLng origin, LatLng destination) {
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + destination.latitude + "," + destination.longitude;
        String mode = "mode=" + "driving";
        String parameter = str_origin + "&" + str_dest + "&" + mode;
        String format = "json";

        return "https://maps.googleapis.com/maps/api/directions/" + format + "?" + parameter + "&key=" + getString(R.string.google_maps_key);

    }

    private void getCurrLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(e -> {

            longitude = e.getLongitude();
            latitude = e.getLatitude();

            System.out.println(latitude + "--------++++----------" + longitude);
            setMarkers(); // call back
        });
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.getLastLocation().addOnCompleteListener(task -> {
            Location location = task.getResult();
            if (location != null) {
                try {

                    mapFragment.getMapAsync(googleMap -> {
                        LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
                        MarkerOptions markerOptions = new MarkerOptions().position(latLng).title("You are Here");

                        googleMap.addMarker(markerOptions);
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17));

                    });

                    Geocoder geocoder = new Geocoder(MapsActivity.this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(
                            location.getLatitude(), location.getLongitude(), 1
                    );
                    curr_location.setText(addresses.get(0).getLocality());

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @SuppressLint("SetTextI18n")
    public void myDay(){
        // The greetings changes depend on the day
        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.HOUR_OF_DAY);

        if(day < 12){
            day_manager.setText("Good Morning,");
            day_manager.setTextSize(40);

        }
        else if(day < 16){
            day_manager.setText("Good Afternoon,");
            day_manager.setTextSize(40);
        }
        else if(day >= 17 && day < 21){
            day_manager.setText("Good Evening,");
            day_manager.setTextSize(40);
        }
        else if(day >= 21){
            day_manager.setText("Good Night,");
            day_manager.setTextSize(40);
        }
        else{
            day_manager.setText("Good Morning,");
            day_manager.setTextSize(40);
        }
    }

    private void go_to_menu(){
        startActivity(new Intent(this, NavMenu.class));
    }

    @Override
    public void onTaskDone(Object... values) {
        if (currentPolyline != null)
            currentPolyline.remove();

        currentPolyline = mMap.addPolyline((PolylineOptions) values[0]);
    }
}