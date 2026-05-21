package com.example.eyalproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eyalproject.databinding.ActivityWelcomeBinding;

/**
 * A landing screen presented to users that acts as the main junction for
 * unauthenticated users to either create a new account or log into an existing one.
 */
public class WelcomeActivity extends AppCompatActivity {

    private ActivityWelcomeBinding binding;

    /**
     * Inflates the layout bindings and executes setup methods on initial creation.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     * shut down then this Bundle contains the data it most recently supplied.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupClickListeners();
    }

    /**
     * Attaches click event listeners to the interactive UI components, mapping them
     * to explicitly launch the intended Register or Login activity intents.
     */
    private void setupClickListeners() {
        binding.registerButton.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
        binding.signInText.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Cleans up the view bindings when the activity reaches the end of its lifecycle
     * to eliminate memory leaks.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}