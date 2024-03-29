package com.sample.sik_ligtas_proto;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnTokenCanceledListener;
import com.google.android.gms.tasks.Task;
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

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, TaskLoadedCallback {

    private LatLng currentLocation;
    private LatLng prevLocation = new LatLng(0.0, 0.0);
    private boolean hasLocationData = false;
    private Marker place1Mark, place2Mark;
    private GoogleMap mMap;
    private MarkerOptions place1, place2;
    private Polyline currentPolyline;
    private AlertDialog alertDialog;

    FusedLocationProviderClient fusedLocationProviderClient;
    LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private boolean hasCollisionNearBy = false; // TODO MAKE FALSE

    List<MarkerOptions> markerOptionsList = new ArrayList<>();
    AppCompatButton menuBtn;
    TextView day_manager, curr_location, userName, nav_title;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;
    String userId;
    Button LocationButton;
    SupportMapFragment mapFragment;
    CountDownTimer countDownTimer;
    private LocationSettingsRequest request;


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
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(4000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    hasLocationData = false;
                    return;
                }
                hasLocationData = true;
            }
        };

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
        LocationButton.setOnClickListener(view -> {
            if(hasLocationData){
                if(!hasCollisionNearBy){
                  zoomToMarkers(20, place1);
                } else {
                    zoomToMarkers(-1, place1, place2);
                }
            } else {
                Toast.makeText(this, "Syncing...", Toast.LENGTH_SHORT).show();
            }
        });

        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        Bundle extras = getIntent().getExtras();
        if(extras != null){
            if(extras.getString("FROM_ACTIVITY").equals("SIMUL_COLLISION")){
                hasCollisionNearBy = true;
                // dialog
                AlertDialog.Builder simulDialog = new AlertDialog.Builder(this);
                simulDialog.setTitle("Nearby Collision Detected");
                simulDialog.setMessage("A nearby collision has been detected. Press 'SHOW' to locate");
                simulDialog.setPositiveButton("SHOW", (dialog, which) -> {
                    // algo show marker poly
                    // (1) Set Marker
                    // (2) Set Polyline
                    trackIncidentInMap();
                    zoomToMarkers(-1, place1, place2);
                    dialog.cancel();
                });
                simulDialog.setNegativeButton("DISMISS", (dialog, which) -> {
                    hasCollisionNearBy = false;
                    dialog.cancel();
                });
                simulDialog.setCancelable(false);
                simulDialog.show();
            }
        }
    }

    private void trackIncidentInMap() {
        LatLng incidentLoc = new LatLng(14.9447777, 120.8899436);
        if(place2Mark != null){
            place2Mark.setPosition(incidentLoc);
        } else {
            place2 = new MarkerOptions().position(incidentLoc).title("Need Urgent Assistance");
            place2Mark = mMap.addMarker(place2);
        }
        includePolyLine();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (ActivityCompat.checkSelfPermission(MapsActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            checkSettingsAndStartLocationUpdates();
        } else {
            ActivityCompat.requestPermissions(MapsActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,}, 44);
        }
    }

    private void checkSettingsAndStartLocationUpdates() {
        // Request for Settings Change
        LocationSettingsRequest request = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build();
        SettingsClient settingClient = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> locationSettingsResponseTask = settingClient.checkLocationSettings(request);
        locationSettingsResponseTask.addOnSuccessListener(locationSettingsResponse -> {
            startLocationUpdates(); // if settings accepted
        });

        // if resolvable request a resolution
        locationSettingsResponseTask.addOnFailureListener(e -> {
            if (e instanceof ResolvableApiException) {
                ResolvableApiException apiException = (ResolvableApiException) e;
                try {
                    apiException.startResolutionForResult(MapsActivity.this, 1001);
                } catch (IntentSender.SendIntentException exception) {
                    exception.printStackTrace();
                }
            }
        });
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    private void stopLocationUpdates(){
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopLocationUpdates();
    }

    private void AlertMe() {

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("We detected an unexpected collision")
                .setMessage("Do you need medical assistance? If you don't respond within 2 minutes, I will notify everyone on your emergency contacts.")
                .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(MapsActivity.this, "Requesting Emergency Services", Toast.LENGTH_SHORT).show();
                        CallServices();
                    }
                })
                .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button noButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                countDownTimer = new CountDownTimer(10000, 1000) {
                    @Override
                    public void onTick(long l) {
                        noButton.setText("CANCEL (" + (l/1000) + ")");
                        noButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                countDownTimer.cancel();
                                dialog.dismiss();
                                Toast.makeText(MapsActivity.this, "Request for Emergency Services Cancelled", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onFinish() {
                        if(dialog.isShowing())
                            dialog.dismiss();
                            Toast.makeText(MapsActivity.this, "Requesting Emergency Services", Toast.LENGTH_SHORT).show();
                            CallServices();
                    }

                }.start();
            }
        });
        dialog.show();

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
        getCurrLocation();
    }

    private void showLastLocationMarker(){
        if(prevLocation.latitude == 0.0 && prevLocation.longitude == 0.0){
            prevLocation = new LatLng(currentLocation.latitude, currentLocation.longitude);
            place1 = new MarkerOptions().position(currentLocation).title("You are Here");
            markerOptionsList.add(place1);
            place1Mark = mMap.addMarker(place1);
        }else if(prevLocation.latitude != currentLocation.latitude || prevLocation.longitude != currentLocation.longitude){
            prevLocation = new LatLng(currentLocation.latitude, currentLocation.longitude);
            place1Mark.setPosition(currentLocation);
        }

        try {
            Geocoder geocoder = new Geocoder(MapsActivity.this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(
                    currentLocation.latitude, currentLocation.longitude, 1
            );
            curr_location.setText(addresses.get(0).getLocality());
        } catch (Exception ignored){
        }

        zoomToMarkers(20, place1);
    }

    private void setMarkers(){
        if(prevLocation.latitude == 0.0 && prevLocation.longitude == 0.0){
            prevLocation = new LatLng(currentLocation.latitude, currentLocation.longitude);
            place1 = new MarkerOptions().position(currentLocation).title("You are Here");
            place2 = new MarkerOptions().position(new LatLng(14.9447777, 120.8899436)).title("Need Urgent Assistance");
            place2Mark = mMap.addMarker(place2);

            markerOptionsList.add(place1);
            markerOptionsList.add(place2);

            place1Mark = mMap.addMarker(place1);
        }else if(prevLocation.latitude != currentLocation.latitude || prevLocation.longitude != currentLocation.longitude){
            prevLocation = new LatLng(currentLocation.latitude, currentLocation.longitude);
            place1Mark.setPosition(currentLocation);
        }
        //includePolyLine();
    }

    private void includePolyLine(){
        new FetchURL(MapsActivity.this)
                .execute(getUrl(place1.getPosition(), place2.getPosition()), "driving");
    }

    private void zoomToMarkers(int zoomLevel, MarkerOptions... markerOptions){
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (MarkerOptions m : markerOptions) {
            builder.include(m.getPosition());
        }

        /**
         * 1: World
         * 5: Landmass/continent
         * 10: City
         * 15: Streets
         * 20: Buildings
         */

        LatLngBounds bounds = builder.build();

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int padding = (int) (width * 0.30);

        if(zoomLevel == -1){
            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding);
            mMap.animateCamera(cu);
        } else {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, zoomLevel));
        }
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

        fusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            showLastLocationMarker(); // call back
                        }
                    }
                });
    }

//    @Deprecated
//    private void getLocation() {
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            return;
//        }
//        fusedLocationProviderClient.getLastLocation().addOnCompleteListener(task -> {
//            Location location = task.getResult();
//            if (location != null) {
//                try {
//
//                    mapFragment.getMapAsync(googleMap -> {
//                        LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
//                        MarkerOptions markerOptions = new MarkerOptions().position(latLng).title("You are Here");
//
//                        googleMap.addMarker(markerOptions);
//                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17));
//
//                    });
//
//                    Geocoder geocoder = new Geocoder(MapsActivity.this, Locale.getDefault());
//                    List<Address> addresses = geocoder.getFromLocation(
//                            location.getLatitude(), location.getLongitude(), 1
//                    );
//                    curr_location.setText(addresses.get(0).getLocality());
//
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//    }

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