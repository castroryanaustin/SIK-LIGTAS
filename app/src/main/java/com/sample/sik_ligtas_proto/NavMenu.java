package com.sample.sik_ligtas_proto;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Objects;

public class NavMenu extends AppCompatActivity {

    TextView fullName, email, phone, back, addContacts, firstAidGuide, credits, help;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;
    String userId;
    Button logout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        back = findViewById(R.id.back);
        addContacts = findViewById(R.id.emergency_contact);
        firstAidGuide = findViewById(R.id.log_first_aid);
        credits = findViewById(R.id.log_credits);

        phone = findViewById(R.id.log_phone);
        fullName = findViewById(R.id.log_name);
        email = findViewById(R.id.log_email);
        logout = findViewById(R.id.btn_logout);
        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        userId = Objects.requireNonNull(fAuth.getCurrentUser()).getUid();
        help = findViewById(R.id.help);

        DocumentReference documentReference = fStore.collection("users").document(userId);
        documentReference.addSnapshotListener(this, (value, error) -> {
            assert value != null;
            fullName.setText(value.getString("fName"));
            email.setText(value.getString("email"));
            phone.setText(value.getString("phone"));
        });

        logout.setOnClickListener(view -> {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(NavMenu.this, Welcome.class));
                finish();
        });
        back.setOnClickListener(v -> finish());

        addContacts.setOnClickListener(v -> startActivity(new Intent(NavMenu.this, MainActivity.class)));

        firstAidGuide.setOnClickListener(v -> openAid());

        credits.setOnClickListener(v -> startActivity(new Intent(NavMenu.this, Credits.class)));

        help.setOnClickListener(v -> startActivity(new Intent(NavMenu.this, AppHelp.class)));


    }

    private void openAid() {
        Intent openAid = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.redcross.org/take-a-class/first-aid/performing-first-aid/first-aid-steps"));
        startActivity(openAid);
    }


}