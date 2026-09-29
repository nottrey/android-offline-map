package com.example.offlinemap;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class RegisterFragment extends Fragment {

    private EditText email, password, username;
    Context context;
    AuthListener authListener;

    public RegisterFragment() {
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // This confirms MainActivity implements AuthListener
        if (context instanceof AuthListener) {
            authListener = (AuthListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement AuthListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.register_frame, container, false);
        TextView signUp = view.findViewById(R.id.tvSignUp);
        Button registerBtn = view.findViewById(R.id.btnRegister);
        email = view.findViewById(R.id.etEmail);
        password = view.findViewById(R.id.etPassword);
        username = view.findViewById(R.id.etUsername);

        registerBtn.setOnClickListener(v ->
        {
            String thisEmail = email.getText().toString().trim();
            String thisPassword = password.getText().toString().trim();
            String thisUsername = username.getText().toString().trim();

            LoginRegister loginHelper = new LoginRegister(authListener, requireContext());

            if (thisEmail.isEmpty() || thisPassword.isEmpty() || thisUsername.isEmpty()) {
                Toast.makeText(context, "Email, username and password cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            EmailValidator validator = new EmailValidator();
            if(!validator.isValidEmail(thisEmail)){
                Toast.makeText(context, "Email isn't valid", Toast.LENGTH_SHORT).show();
                return;
            }
            loginHelper.checkIfUsernameExists(thisUsername, thisEmail, thisPassword);
        });

        signUp.setOnClickListener(v -> {
            Fragment fragment = new LoginFragment();
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment) // Use the same ID from activity_main.xml
                    .addToBackStack(null) // Allows user to go back to Login when pressing 'Back'
                    .commit();
        });


        return view;
    }
}