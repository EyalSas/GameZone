package com.example.eyalproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * An activity that manages user login operations. Validates input credentials,
 * connects to Firebase Authentication via FirebaseHelper, and routes the user
 * to the main activity upon a successful login.
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputEditText editTextUsername, editTextPassword;
    private TextInputLayout usernameLayout, passwordLayout;
    private Button buttonLogin, buttonRegister;
    private ProgressBar loginProgress;
    private FirebaseHelper firebaseHelper;

    /**
     * Called when the activity is starting. Initializes the UI components, creates the
     * FirebaseHelper instance, and sets up click listeners for the login and registration buttons.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     * shut down then this Bundle contains the data it most recently
     * supplied.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initializeViews();
        firebaseHelper = new FirebaseHelper();
        buttonLogin.setOnClickListener(v -> validateAndLogin());
        buttonRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
    }

    /**
     * Binds the layout elements to their respective object instances.
     */
    private void initializeViews() {
        usernameLayout  = findViewById(R.id.usernameLayout);
        passwordLayout  = findViewById(R.id.passwordLayout);
        editTextUsername = findViewById(R.id.username1);
        editTextPassword = findViewById(R.id.password1);
        buttonLogin     = findViewById(R.id.btnsignin1);
        buttonRegister  = findViewById(R.id.buttonRegister);
        loginProgress   = findViewById(R.id.loginProgress);
    }

    /**
     * Validates the input fields to ensure they are not empty before attempting
     * an authentication process. Displays inline errors if validation fails.
     */
    private void validateAndLogin() {
        usernameLayout.setError(null);
        passwordLayout.setError(null);

        String username = editTextUsername.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (username.isEmpty()) {
            usernameLayout.setError("Please enter your username");
            return;
        }
        if (password.isEmpty()) {
            passwordLayout.setError("Please enter your password");
            return;
        }

        authenticateUser(username, password);
    }

    /**
     * Invokes the Firebase authentication logic to log the user in with the provided credentials.
     * Controls the visibility of the loading state and responds to authentication callbacks.
     *
     * @param username The username input by the user.
     * @param password The password input by the user.
     */
    private void authenticateUser(String username, String password) {
        loginProgress.setVisibility(View.VISIBLE);
        buttonLogin.setEnabled(false);

        firebaseHelper.loginUserByUsername(username, password, new FirebaseHelper.AuthCallback() {
            @Override
            public void onSuccess(String retrievedUsername) {
                loginProgress.setVisibility(View.GONE);
                navigateToMainActivity(retrievedUsername);
            }

            @Override
            public void onFailure(String error) {
                loginProgress.setVisibility(View.GONE);
                buttonLogin.setEnabled(true);
                passwordLayout.setError("Login failed: " + error);
            }
        });
    }

    /**
     * Navigates the user to the MainActivity upon successful login, transferring the username
     * as an extra and clearing the activity backstack.
     *
     * @param username The successfully authenticated username.
     */
    private void navigateToMainActivity(String username) {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("USERNAME", username);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}