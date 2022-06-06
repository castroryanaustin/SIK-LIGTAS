package com.sample.sik_ligtas_proto;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Welcome extends AppCompatActivity {

    Button wel_login;
    Button wel_signup;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user= FirebaseAuth.getInstance().getCurrentUser();
        if (user != null){
            startActivity(new Intent(Welcome.this,MapsActivity.class));
            Toast.makeText(this, "Welcome Back!", Toast.LENGTH_SHORT).show();
        } else {
            prompt();
        }
    }

    private void prompt() {
        setContentView(R.layout.layout_sign_in);
        wel_login = findViewById(R.id.sign_in_btn);
        wel_signup = findViewById(R.id.sign_in_email);
        wel_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Welcome.this,Login.class));
            }
        });
        wel_signup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Welcome.this,Register.class));
            }
        });
    }
}