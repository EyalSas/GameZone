package com.example.eyalproject;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * An activity serving as the entry point of the app, displaying an animated splash screen before
 * transitioning to the WelcomeActivity. Controls sequential and grouped view animations
 * to create an engaging initial user experience.
 */
public class SplashScreenActivity extends AppCompatActivity {

    /**
     * Total duration in milliseconds that the splash screen remains visible
     * before automatically transitioning to the next activity.
     */
    private static final long SPLASH_SCREEN_TIMEOUT = 4000;

    // Background decorative elements
    private ImageView bgCircle1, bgCircle2;

    // Text elements displayed sequentially on the screen
    private TextView welcomeText, taglineText, loadingText, versionText;

    // Visual indicator showing the user that the app is "loading"
    private LinearProgressIndicator progressBar;

    // Main container for the application logo
    private MaterialCardView logoCard;

    /**
     * Called when the splash screen activity initializes. Hides the action bar,
     * resolves layout views, and triggers the initial animation sequence.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     * shut down then this Bundle contains the data it most recently
     * supplied. Otherwise, it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the user interface layout for this Activity
        setContentView(R.layout.splash_screen);

        // Hide the default Action Bar to achieve a full-screen, immersive look
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Bind layout elements to local variables and prepare them for animation
        initializeViews();

        // Wait for the layout to finish measuring before calculating and starting animations
        setupAnimations();
    }

    /**
     * Binds internal variables to the layout components and resets initial alpha values
     * to zero, ensuring they remain invisible until their specific entrance animations trigger.
     */
    private void initializeViews() {
        // Link Java objects to XML layout IDs
        logoCard      = findViewById(R.id.logoCard);
        welcomeText   = findViewById(R.id.welcomeText);
        taglineText   = findViewById(R.id.taglineText);
        loadingText   = findViewById(R.id.loadingText);
        versionText   = findViewById(R.id.versionText);
        progressBar   = findViewById(R.id.progressBar);
        bgCircle1     = findViewById(R.id.bgCircle1);
        bgCircle2     = findViewById(R.id.bgCircle2);

        // Hide textual and progress elements initially to prevent visual flashing.
        // They will be faded in gradually via animations.
        welcomeText.setAlpha(0f);
        taglineText.setAlpha(0f);
        loadingText.setAlpha(0f);
        versionText.setAlpha(0f);
        progressBar.setAlpha(0f);
    }

    /**
     * Attaches a global layout listener to ensure views are measured and drawn
     * before animations begin, kicking off the primary logo animation once the layout is ready.
     */
    private void setupAnimations() {
        // Add a listener that triggers right after the view hierarchy is fully measured
        logoCard.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Remove the listener immediately so it doesn't fire repeatedly on subsequent layout passes
                logoCard.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // Now that sizes and positions are known, start the first visual sequence
                startLogoAnimation();
            }
        });
    }

    /**
     * Animates the main application logo onto the screen using scaling, rotation, and alpha changes.
     * Schedules subsequent text and progress animations to run sequentially via timed Handlers.
     */
    private void startLogoAnimation() {
        // Create an AnimatorSet to run multiple ObjectAnimators simultaneously
        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(
                ObjectAnimator.ofFloat(logoCard, "scaleX",   0f, 1f),       // Grow horizontally
                ObjectAnimator.ofFloat(logoCard, "scaleY",   0f, 1f),       // Grow vertically
                ObjectAnimator.ofFloat(logoCard, "rotation", -180f, 0f),    // Spin into place
                ObjectAnimator.ofFloat(logoCard, "alpha",    0f, 1f)        // Fade in
        );

        // Make the logo animation last 1.2 seconds
        logoSet.setDuration(1200);
        // Use an OvershootInterpolator to make the logo "pop" past its final size and settle back
        logoSet.setInterpolator(new OvershootInterpolator(1.2f));
        logoSet.start();

        // Queue the subsequent entrance animations with staggered delays
        new Handler().postDelayed(this::animateWelcomeText,  400);   // Starts at 0.4s
        new Handler().postDelayed(this::animateTaglineText,  800);   // Starts at 0.8s
        new Handler().postDelayed(this::animateLoadingSection, 1200);  // Starts at 1.2s
        new Handler().postDelayed(this::animateProgressAndVersion, 1600); // Starts at 1.6s

        // Schedule the transition to the next screen once the total timeout is reached
        new Handler().postDelayed(this::navigateToNextActivity, SPLASH_SCREEN_TIMEOUT);
    }

    /**
     * Animates the main "Welcome" text, making it rise into place while simultaneously fading in.
     */
    private void animateWelcomeText() {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                // Fade from invisible to fully visible
                ObjectAnimator.ofFloat(welcomeText, "alpha",        0f, 1f),
                // Slide up from 50 pixels below its actual position to its final position (0f)
                ObjectAnimator.ofFloat(welcomeText, "translationY", 50f, 0f)
        );
        set.setDuration(800);
        set.setInterpolator(new AccelerateDecelerateInterpolator()); // Smooth start and end
        set.start();
    }

    /**
     * Animates the sub-tagline expanding and fading into place utilizing a distinct bounce effect.
     */
    private void animateTaglineText() {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(taglineText, "alpha",  0f, 1f),
                // Start slightly smaller (80%) and scale to normal size (100%)
                ObjectAnimator.ofFloat(taglineText, "scaleX", 0.8f, 1f),
                ObjectAnimator.ofFloat(taglineText, "scaleY", 0.8f, 1f)
        );
        set.setDuration(600);
        // Use a BounceInterpolator to give the text a playful, spring-like feel
        set.setInterpolator(new BounceInterpolator());
        set.start();
    }

    /**
     * Triggers the initial fade-in of the loading text section, and immediately
     * queues a continuous pulsating effect to indicate ongoing activity.
     */
    private void animateLoadingSection() {
        // Fade the text into view over 600ms
        ObjectAnimator textAlpha = ObjectAnimator.ofFloat(loadingText, "alpha", 0f, 1f);
        textAlpha.setDuration(600);
        textAlpha.start();

        // Begin the infinite breathing/pulsing animation
        startPulseAnimation(loadingText);
    }

    /**
     * Animates the visibility of the primary progress bar and the application version string.
     */
    private void animateProgressAndVersion() {
        // Fade in the horizontal loading bar
        ObjectAnimator progressAlpha = ObjectAnimator.ofFloat(progressBar, "alpha", 0f, 1f);
        progressAlpha.setDuration(600);
        progressAlpha.start();

        // Fade in the text at the bottom displaying the app version
        ObjectAnimator versionAlpha = ObjectAnimator.ofFloat(versionText, "alpha", 0f, 1f);
        versionAlpha.setDuration(600);
        versionAlpha.start();
    }

    /**
     * Applies an infinite repeating scale-in and scale-out animation to simulate a breathing
     * or pulsing visual effect on the target view.
     *
     * @param view The target UI element to animate.
     */
    private void startPulseAnimation(android.view.View view) {
        // Define scale animations from 100% to 110% and back to 100%
        ObjectAnimator sx = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f);

        // Make one full pulse cycle take 2 seconds
        sx.setDuration(2000);
        sy.setDuration(2000);

        // Loop the animation endlessly until the view is destroyed
        sx.setRepeatCount(ObjectAnimator.INFINITE);
        sy.setRepeatCount(ObjectAnimator.INFINITE);

        // Ensure smooth transitions back and forth
        sx.setInterpolator(new AccelerateDecelerateInterpolator());
        sy.setInterpolator(new AccelerateDecelerateInterpolator());

        sx.start();
        sy.start();
    }

    /**
     * Prepares to switch to the WelcomeActivity by reversing the logo animation (scaling down
     * and fading out) and defining a screen transition sequence once the exit animation concludes.
     */
    private void navigateToNextActivity() {
        // Create an exit animation set for the logo
        AnimatorSet exitSet = new AnimatorSet();
        exitSet.playTogether(
                ObjectAnimator.ofFloat(logoCard, "alpha",  1f, 0f),      // Fade out
                ObjectAnimator.ofFloat(logoCard, "scaleX", 1f, 0.8f),    // Shrink horizontally
                ObjectAnimator.ofFloat(logoCard, "scaleY", 1f, 0.8f)     // Shrink vertically
        );
        exitSet.setDuration(500);
        exitSet.setInterpolator(new AccelerateInterpolator()); // Speed up as it disappears
        exitSet.start();

        // Listen for the exact moment the exit animation finishes
        exitSet.addListener(new android.animation.Animator.AnimatorListener() {
            @Override public void onAnimationStart(android.animation.Animator a) {}
            @Override public void onAnimationCancel(android.animation.Animator a) {}
            @Override public void onAnimationRepeat(android.animation.Animator a) {}

            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                // Prepare to open the next screen
                Intent intent = new Intent(SplashScreenActivity.this, WelcomeActivity.class);
                startActivity(intent);

                // Override default Android screen transition with custom fade in/out
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

                // Remove Splash Screen from the backstack so the user can't navigate back to it
                finish();
            }
        });
    }

    /**
     * Called when the activity is destroyed. Cleans up any running view animations to prevent
     * potential memory leaks or zombie states if the user exits the app during the splash screen.
     */
    @Override
    protected void onDestroy() {
        // Halt any ongoing animations to free up system resources
        if (logoCard  != null) logoCard.clearAnimation();
        if (loadingText != null) loadingText.clearAnimation();
        if (bgCircle1 != null) bgCircle1.clearAnimation();
        if (bgCircle2 != null) bgCircle2.clearAnimation();

        super.onDestroy();
    }
}