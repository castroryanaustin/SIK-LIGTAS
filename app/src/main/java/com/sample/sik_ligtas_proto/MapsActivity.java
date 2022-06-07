package com.sample.sik_ligtas_proto;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sample.sik_ligtas_proto.DirectionHelpers.FetchURL;
import com.sample.sik_ligtas_proto.DirectionHelpers.TaskLoadedCallback;
import com.sample.sik_ligtas_proto.databinding.ActivityMapsBinding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, TaskLoadedCallback {

    private GoogleMap mMap;
    private MarkerOptions place1, place2;
    private Polyline currentPolyline;

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
    public void onBackPressed() {
        super.onBackPressed();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                go_to_menu();
            }
        });

        myDay();

        LocationButton = findViewById(R.id.LocationButton);
        LocationButton.setOnClickListener(view -> new FetchURL(MapsActivity.this)
                .execute(getUrl(place1.getPosition(), place2.getPosition()), "driving"));


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.getLastLocation().addOnCompleteListener(e-> {
            Location location = e.getResult();
            this.longitude = location.getLongitude();
            this.latitude = location.getLatitude();
            System.out.println(this.longitude + "----------------------------" + this.latitude);
        });
        System.out.println(this.longitude + "----------------------------" + this.latitude);
        place1 = new MarkerOptions().position(new LatLng(this.latitude, this.longitude)).title("Location1");
        place2 = new MarkerOptions().position(new LatLng(14.944777, 120.889943)).title("Location2");

        markerOptionsList.add(place1);
        markerOptionsList.add(place2);

        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
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

    private double longitude, latitude;

    private void getCurrLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.getLastLocation().addOnCompleteListener(e-> {
            Location location = e.getResult();
                this.longitude = location.getLongitude();
                this.latitude = location.getLatitude();
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