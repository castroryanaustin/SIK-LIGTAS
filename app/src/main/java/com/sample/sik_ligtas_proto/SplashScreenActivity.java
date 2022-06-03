package com.sample.sik_ligtas_proto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthMethodPickerLayout;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.sample.sik_ligtas_proto.Model.DriverInfoModel;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class SplashScreenActivity extends AppCompatActivity {

    private final static int LOGIN_REQUEST_CODE = 7171;
    private List<AuthUI.IdpConfig> providers;
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener listener;

    @BindView(R.id.progress_bar)
    ProgressBar progress_Bar;

    FirebaseDatabase database;
    DatabaseReference driverInfoRef;

    @Override
    protected void onStart() {
        super.onStart();
        delaySplashScreen();
    }

    @Override
    protected void onStop() {
        if(firebaseAuth != null && listener != null)
            firebaseAuth.removeAuthStateListener(listener);
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
        init();
    }

    private void init() {

        ButterKnife.bind(this);

        database = FirebaseDatabase.getInstance();
        driverInfoRef = database.getReference(Common.DRIVER_INFO_REFERENCE);
        providers = Arrays.asList(
                new AuthUI.IdpConfig.PhoneBuilder().build(),
                new AuthUI.IdpConfig.GoogleBuilder().build());

        firebaseAuth = FirebaseAuth.getInstance();
        listener = myFirebaseAuth -> {
            FirebaseUser user = myFirebaseAuth.getCurrentUser();
            if(user != null){
                checkUserFromFirebase();
            }
            else{
                //showLoginLayout();
                Button emailBtn = findViewById(R.id.sign_in_w_google_btn);
                emailBtn.setOnClickListener(e->{
                    startActivity(new Intent(this,sign_in_email.class));
                });
            }
        };
    }

    private void checkUserFromFirebase() {
        driverInfoRef.child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if(snapshot.exists()){
                            Toast.makeText(SplashScreenActivity.this, "User already registered", Toast.LENGTH_SHORT).show();
                        }
                        else {
                            showRegisterLayout();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(SplashScreenActivity.this, ""+error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showRegisterLayout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DialogTheme);
        View itemView = LayoutInflater.from(this).inflate(R.layout.layout_register,null);

        TextInputEditText first_name = (TextInputEditText)itemView.findViewById(R.id.reg_first_name);
        TextInputEditText last_name = (TextInputEditText)itemView.findViewById(R.id.reg_last_name);
        TextInputEditText phone_number = (TextInputEditText)itemView.findViewById(R.id.reg_phone_number);

        Button btn_register = (Button)itemView.findViewById(R.id.btn_register);
        if(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber() != null &&
                TextUtils.isEmpty(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber()))
        phone_number.setText(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber());

        builder.setView(itemView);
        AlertDialog dialog = builder.create();
        dialog.show();

        btn_register.setOnClickListener(view -> {
            if(TextUtils.isEmpty(first_name.getText().toString())){
                Toast.makeText(this, "Please enter your First Name", Toast.LENGTH_SHORT).show();
                return;
            }
            else if(TextUtils.isEmpty(last_name.getText().toString())){
                Toast.makeText(this, "Please enter your Last Name", Toast.LENGTH_SHORT).show();
                return;
            }
            else if(TextUtils.isEmpty(phone_number.getText().toString())){
                Toast.makeText(this, "Please enter your Phone Number", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                return;
            }
            else {
                DriverInfoModel model = new DriverInfoModel();
                model.setFirstName(first_name.getText().toString());
                model.setLastName(last_name.getText().toString());
                model.setPhoneNumber(phone_number.getText().toString());
                model.setRating(0.0);

                driverInfoRef.child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                        .setValue(model)
                        .addOnFailureListener(e ->
                                {
                                    dialog.dismiss();
                                    Toast.makeText(SplashScreenActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                        )
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Registered Successfully!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        });
            }
        });
    }

    private void showLoginLayout() {
        AuthMethodPickerLayout authMethodPickerLayout = new AuthMethodPickerLayout
                .Builder(R.layout.layout_sign_in)
                .setPhoneButtonId(R.id.sign_in_btn)
                //.setGoogleButtonId(R.id.sign_in_w_google_btn)
                .build();
        startActivityForResult(AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setIsSmartLockEnabled(false)
                .setTheme(R.style.LoginTheme)
                .setAvailableProviders(providers)
                .build(),LOGIN_REQUEST_CODE);
    }

    private void delaySplashScreen() {

        progress_Bar.setVisibility(View.VISIBLE);

        Completable.timer(3,TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .subscribe(() ->

                        firebaseAuth.addAuthStateListener(listener)

                        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == LOGIN_REQUEST_CODE){
            IdpResponse response = IdpResponse.fromResultIntent(data);
            if(resultCode == RESULT_OK){
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                startActivity(new Intent(SplashScreenActivity.this,MapsActivity.class));

            }
            else{
                Toast.makeText(this, "[ERROR]:"+response.getError().getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}