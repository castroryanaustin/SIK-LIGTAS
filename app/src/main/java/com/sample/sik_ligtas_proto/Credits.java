package com.sample.sik_ligtas_proto;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class Credits extends AppCompatActivity {

    Button goBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credits);

        goBack = findViewById(R.id.btn_back);

        goBack.setOnClickListener(view -> finish());
    }
}