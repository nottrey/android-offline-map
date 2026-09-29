package com.example.offlinemap;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.concurrent.Executor;

interface AuthListener {
    void onAuthSuccess();
    void onAuthFailure(String errorMessage);
}


public class LoginRegister {
    private FirebaseAuth mAuth;
    private DatabaseReference usersDatabase;

    private final AuthListener authListener;
    Context context;
    private static final String TAG = "login";
    public LoginRegister(AuthListener authListener, Context context){
        this.authListener = authListener;
        mAuth = FirebaseAuth.getInstance();
        usersDatabase = FirebaseDatabase.getInstance().getReference().child("users");
        this.context = context;
    }



    private void createAccount(String email, String password, String username) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail: success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            writeNewUser(username, email, user.getUid());
                            if (authListener != null) {
                                authListener.onAuthSuccess();
                            }
                        } else {
                            if (authListener != null) {
                                authListener.onAuthFailure("Failed to get user profile after creation.");
                            }
                        }
                    } else {
                        Log.w(TAG, "createUserWithEmail: failure", task.getException());
                        if (authListener != null) {
                            authListener.onAuthFailure(task.getException().getMessage());
                        }
                    }
                });
    }


    private void writeNewUser(String username, String email, String userId){
        User user = new User(username, email, userId);
        Log.d(TAG, "write new user to realtime db: "+ username);
        FirebaseDatabase.getInstance().getReference("users").child(userId).setValue(user);
    }
    public void checkIfUsernameExists(String username, String email, String password) {

        usersDatabase.orderByChild("username").equalTo(username)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                        if (dataSnapshot.exists()) {
                            Toast.makeText(context, "Username is already taken. Please choose another.", Toast.LENGTH_SHORT).show();
                        } else {

                            createAccount(email, password, username);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        // This is called if there was an error performing the query.
                        Log.w(TAG, "Database query failed: ", databaseError.toException());
                        Toast.makeText(context, "Database error. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void signIn(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        //success
                        Log.d(TAG, "signInWithEmail: success");
                        if (authListener != null) {
                            //onAuthSuccess() in MainActivity
                            authListener.onAuthSuccess();
                        }
                    } else {
                        // failure
                        Log.w(TAG, "signInWithEmail: failure", task.getException());
                        if (authListener != null) {
                            authListener.onAuthFailure(task.getException().getMessage());
                        }
                    }
                });
    }

    public void signOut() {
        mAuth.signOut();
        //updateUI(null);
    }

}
