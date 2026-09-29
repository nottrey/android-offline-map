package com.example.offlinemap;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AuthListener {

    Intent intent;
    Button loginBtn, registerBtn;

    EditText email, username, password;
    private FirebaseAuth mAuth;

    LinearLayout mainLayout;

    LoginRegister loginHelper;
    private FirebaseAuth.AuthStateListener authStateListener;
    private SharedPreferences sessionPrefs;
    private boolean mapOpened = false;

    private static final int REQ_LOCATION = 1001;
    private static final int REQ_NOTIFICATIONS = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        sessionPrefs = getSharedPreferences("user_session", MODE_PRIVATE);
        mAuth = FirebaseAuth.getInstance();
        authStateListener = firebaseAuth -> {
            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
            if (currentUser != null) {
                cacheUserSession(currentUser);
                openMap();
            }
        };

        loginHelper = new LoginRegister(this, this);
        loginBtn = findViewById(R.id.btnLogin);
        loginBtn.setOnClickListener(this);
        registerBtn = findViewById(R.id.btnRegister);
        registerBtn.setOnClickListener(this);
        mainLayout = findViewById(R.id.layout);

    }
    @Override
    public void onStart(){
        super.onStart();
        mAuth = FirebaseAuth.getInstance();
        mAuth.addAuthStateListener(authStateListener);
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            cacheUserSession(currentUser);
            requestNotificationPermissionIfNeeded();
            openMap();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAuth != null && authStateListener != null) {
            mAuth.removeAuthStateListener(authStateListener);
        }
    }

    public void openMap() {
        if (mapOpened) return;

        intent = new Intent(this, DisplayAllMaps.class);
        if (hasLocationPermission()) {
            mapOpened = true;
            startActivity(intent);
            finish();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION
            );
        }


    }

    @Override
    public void onAuthSuccess() {
        Toast.makeText(getApplicationContext(), "Login succeess.", Toast.LENGTH_SHORT).show();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            cacheUserSession(currentUser);
        }
        requestNotificationPermissionIfNeeded();
        openMap();
    }
    @Override
    public void onAuthFailure(java.lang.String errorMessage) {
        Toast.makeText(this, "Login Failed: " + errorMessage, Toast.LENGTH_LONG).show();
    }


    @Override
    public void onClick(View v) {
        if(v==registerBtn){
            mainLayout.setVisibility(View.GONE);
            Fragment fragment = new RegisterFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }

        else if(v==loginBtn){
            mainLayout.setVisibility(View.GONE);
            Fragment fragment = new LoginFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (hasLocationPermission()) {
                mapOpened = true;
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_NOTIFICATIONS) {
            // No-op: app continues even if user denies notifications
        }
    }

    private void cacheUserSession(FirebaseUser user) {
        sessionPrefs.edit()
                .putString("uid", user.getUid())
                .putString("email", user.getEmail())
                .apply();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATIONS
            );
        }
    }
}
