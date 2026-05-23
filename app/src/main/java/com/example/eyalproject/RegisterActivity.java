package com.example.eyalproject;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * An activity that manages new user registration. Validates user input such as email formats
 * and matching passwords, attempts to register via Firebase Auth, and creates the corresponding
 * user profile using FirebaseHelper.
 */
public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText editTextUsername, editTextEmail, editTextPassword, editTextRePassword;
    private TextInputLayout usernameLayout, emailLayout, passwordLayout, rePasswordLayout;
    private Button buttonSignUp, buttonLogin;
    private ProgressBar registerProgress;
    private FirebaseHelper firebaseHelper;

    /**
     * Called when the activity is created. Sets up the initial UI bindings, assigns click listeners
     * to the action buttons, and instantiates the Firebase helper class.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     * shut down then this Bundle contains the data it most recently supplied.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the visual layout for the registration screen
        setContentView(R.layout.activity_register);
        // Link UI components and initialize the database helper
        initializeViews();
        firebaseHelper = new FirebaseHelper();

        // Trigger the registration validation flow when the sign-up button is clicked
        buttonSignUp.setOnClickListener(v -> validateAndRegister());
        // Navigate back to the login screen if the user already has an account
        buttonLogin.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            // Ensure we don't create multiple instances of the Login screen in the backstack
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
    }

    /**
     * Maps local variables to the corresponding view items defined in the layout XML file.
     */
    private void initializeViews() {
        // Link the layout wrappers (used for displaying inline error messages)
        usernameLayout   = findViewById(R.id.usernameLayout);
        emailLayout      = findViewById(R.id.emailLayout);
        passwordLayout   = findViewById(R.id.passwordLayout);
        rePasswordLayout = findViewById(R.id.repasswordLayout);
        // Link the actual text input fields
        editTextUsername = findViewById(R.id.username);
        editTextEmail    = findViewById(R.id.gmailedit);
        editTextPassword = findViewById(R.id.password);
        editTextRePassword = findViewById(R.id.repassword);
        // Link the interactive buttons and the loading spinner
        buttonSignUp     = findViewById(R.id.btnsignup);
        buttonLogin      = findViewById(R.id.buttonLogin);
        registerProgress = findViewById(R.id.registerProgress);
    }

    /**
     * Executes the primary registration flow: clearing error states, extracting input values,
     * validating those values, and initiating the Firebase backend registration process.
     */
    private void validateAndRegister() {
        // Clear any previous error states before running new checks
        clearErrors();
        // Extract the text from the input fields and remove accidental leading/trailing spaces
        String username   = editTextUsername.getText().toString().trim();
        String email      = editTextEmail.getText().toString().trim();
        String password   = editTextPassword.getText().toString().trim();
        String rePassword = editTextRePassword.getText().toString().trim();
        // Halt execution immediately if any input fails the validation rules
        if (!isInputValid(username, email, password, rePassword)) return;
        // Proceed to communicate with Firebase if all inputs are clean
        showProgressAndRegister(username, email, password);
    }

    /**
     * Removes all active error messages currently displayed on the TextInputLayouts.
     */
    private void clearErrors() {
        // Setting the error to null hides the red warning text and resets the field color
        usernameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        rePasswordLayout.setError(null);
    }

    /**
     * Disables the sign up button, displays a progress bar, and sends the user credentials
     * to Firebase for account creation. Upon success, routes the user to MainActivity.
     *
     * @param username The chosen username.
     * @param email    The user's email address.
     * @param password The desired password.
     */
    private void showProgressAndRegister(String username, String email, String password) {
        // Show the loading spinner and disable the button to prevent multiple submissions
        registerProgress.setVisibility(View.VISIBLE);
        buttonSignUp.setEnabled(false);
        // Execute the registration call via the centralized FirebaseHelper
        firebaseHelper.registerUser(username, email, password, new FirebaseHelper.AuthCallback() {
            @Override
            public void onSuccess(String retrievedUsername) {
                // Hide spinner, notify user, and move to the main app dashboard
                registerProgress.setVisibility(View.GONE);
                showToast("Account created successfully!");
                navigateToMainActivity(retrievedUsername);
            }
            @Override
            public void onFailure(String error) {
                // Hide spinner, re-enable the button so they can try again, and show the error
                registerProgress.setVisibility(View.GONE);
                buttonSignUp.setEnabled(true);
                showToast("Registration failed: " + error);
            }
        });
    }

    /**
     * Performs a series of checks on the provided inputs, verifying that fields aren't empty,
     * the email is correctly formatted, and that the passwords match and meet minimum lengths.
     *
     * @param username   The raw text from the username input.
     * @param email      The raw text from the email input.
     * @param password   The raw text from the password input.
     * @param rePassword The raw text from the repeat-password input.
     * @return True if all input criteria are satisfied, False otherwise.
     */
    private boolean isInputValid(String username, String email, String password, String rePassword) {
        // Check for missing data
        if (username.isEmpty()) { usernameLayout.setError("Username is required"); return false; }
        if (email.isEmpty()) { emailLayout.setError("Email is required"); return false; }
        // Verify the email string looks like a real email address (e.g., name@domain.com)
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailLayout.setError("Please enter a valid email address"); return false; }
        if (password.isEmpty()) { passwordLayout.setError("Password is required"); return false; }
        // Firebase Authentication enforces a strict minimum of 6 characters for passwords
        if (password.length() < 6) { passwordLayout.setError("Password must be at least 6 characters"); return false; }
        if (rePassword.isEmpty()) { rePasswordLayout.setError("Please confirm your password"); return false; }
        // Ensure the user typed the exact same password in both boxes
        if (!password.equals(rePassword)) { rePasswordLayout.setError("Passwords do not match"); return false; }
        // All checks passed
        return true;
    }

    /**
     * Convenience method to show a brief toast message.
     *
     * @param message The message text to display.
     */
    private void showToast(String message) {
        // Pop up a brief system message at the bottom of the screen
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Transfers control to the MainActivity, clearing the backstack so the user cannot navigate
     * back to the registration screen.
     *
     * @param username The username assigned during registration.
     */
    private void navigateToMainActivity(String username) {
        // Create an intent pointing to the main dashboard
        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
        // Pass the newly registered username to the MainActivity so it can greet the user
        intent.putExtra("USERNAME", username);
        // Clear all previous activities from memory so pressing 'Back' closes the app instead of returning here
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        // Destroy the registration activity
        finish();
    }
}