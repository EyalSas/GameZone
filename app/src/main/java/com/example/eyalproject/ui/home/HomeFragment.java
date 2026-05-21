package com.example.eyalproject.ui.home;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import java.util.Collections;
import java.util.List;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.eyalproject.FirebaseHelper;
import com.example.eyalproject.MainActivity;
import com.example.eyalproject.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.squareup.picasso.Picasso;

import java.util.Locale;

/**
 * A fragment representing the home dashboard of the application.
 * It provides users with quick navigation links, store statistics,
 * and an interactive carousel showcasing available products.
 */
public class HomeFragment extends Fragment {

    // Navigation and Action Buttons
    private MaterialButton buttonAbout, buttonFeatures, buttonContact;

    // Text elements for titles and statistics
    private TextView  statsValue1, statsValue2, statsValue3;

    // Cards acting as containers for statistics and the main logo
    private MaterialCardView statsCard1, statsCard2, statsCard3;

    // Handler used to manage delayed tasks and animation timings
    private Handler animationHandler = new Handler();

    /**
     * Called to have the fragment instantiate its user interface view.
     * Initializes layout components, assigns interaction listeners, and triggers entrance animations.
     *
     * @param inflater           The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container          If non-null, this is the parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state.
     * @return Return the View for the fragment's UI.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment, creating the view hierarchy
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        // Bind the UI elements from the XML to the local variables
        initializeViews(rootView);

        // Setup all the click behaviors for buttons and cards
        setupClickListeners();

        // Begin the sequence of entrance animations for a smooth user experience
        startAnimations();

        // Return the fully initialized view to be displayed on screen
        return rootView;
    }

    /**
     * Binds local variables to their respective views declared in the XML layout.
     *
     * @param rootView The root view hierarchy of the fragment used to find child views.
     */
    private void initializeViews(View rootView) {
        // Find and assign navigation buttons
        buttonAbout = rootView.findViewById(R.id.buttonAbout);
        buttonFeatures = rootView.findViewById(R.id.buttonFeatures);
        buttonContact = rootView.findViewById(R.id.buttonContact);

        // Find and assign text views
        statsValue1 = rootView.findViewById(R.id.statsValue1);
        statsValue2 = rootView.findViewById(R.id.statsValue2);
        statsValue3 = rootView.findViewById(R.id.statsValue3);

        // Find and assign material card views
        statsCard1 = rootView.findViewById(R.id.statsCard1);
        statsCard2 = rootView.findViewById(R.id.statsCard2);
        statsCard3 = rootView.findViewById(R.id.statsCard3);
    }

    /**
     * Attaches click event listeners to the primary navigation buttons and customizes
     * their click feedback animations.
     */
    private void setupClickListeners() {
        // Handle "About" button click: animate and navigate to the About Fragment
        buttonAbout.setOnClickListener(v -> {
            animateModernButtonClick(v, () -> {
                // Uses Navigation to move to the designated destination
                Navigation.findNavController(v).navigate(R.id.action_navigation_home_to_aboutFragment);
            });
        });

        // Handle "Features" button click: animate and open the product carousel popup
        buttonFeatures.setOnClickListener(v -> {
            animateModernButtonClick(v, this::showRandomProductCarousel);
        });

        // Handle "Contact" button click: animate and navigate to the Service Fragment
        buttonContact.setOnClickListener(v -> {
            animateModernButtonClick(v, () -> {
                Navigation.findNavController(v).navigate(R.id.navigation_service);
            });
        });

        // Initialize click listeners specifically for the statistics cards
        setupStatsCardsInteractions();
    }

    /**
     * Configures interactive click listeners for the informational statistic cards,
     * displaying custom designed toasts with descriptive information when tapped.
     */
    private void setupStatsCardsInteractions() {
        // Interaction for the First Stat Card (Stores)
        statsCard1.setOnClickListener(v -> {
            animateStatsCardClick(v, () -> showDesignedToast(
                    "📍 9 Stores Nationwide",
                    "Click 'About Our Stores' to find directions and contact info.",
                    R.color.stats_card_blue,
                    R.drawable.ic_store
            ));
        });

        // Interaction for the Second Stat Card (Products)
        statsCard2.setOnClickListener(v -> {
            animateStatsCardClick(v, () -> showDesignedToast(
                    "🎮 170+ Products Available",
                    "Explore our extensive catalog covering all your gaming needs.",
                    R.color.stats_card_green,
                    R.drawable.ic_store
            ));
        });

        // Interaction for the Third Stat Card (Support)
        statsCard3.setOnClickListener(v -> {
            animateStatsCardClick(v, () -> showDesignedToast(
                    "🛡️ 24/7 Support",
                    "Need help? Contact support directly via the Service tab.",
                    R.color.stats_card_purple,
                    R.drawable.ic_store
            ));
        });
    }

    /**
     * Applies a quick press-down and release scaling animation to a statistics card.
     *
     * @param v      The card view being animated.
     * @param action A runnable to execute immediately after the animation completes.
     */
    private void animateStatsCardClick(View v, Runnable action) {
        // Step 1: Scale down slightly to simulate pressing the card
        v.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> {
                    // Step 2: Scale back to original size (release)
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .withEndAction(action) // Trigger the provided action (e.g., showing the toast)
                            .start();
                })
                .start();
    }

    /**
     * Displays a custom stylized Toast message to provide detailed feedback.
     * Falls back to a standard Toast if layout inflation fails.
     *
     * @param title    The bold title string.
     * @param message  The detailed message string.
     * @param colorRes The resource ID of the color to use for the card background.
     * @param iconRes  The resource ID for the icon to display.
     */
    private void showDesignedToast(String title, String message, int colorRes, int iconRes) {
        // Prevent crashes if the fragment is not attached to a context
        if (getContext() == null) return;

        try {
            // Inflate the custom XML layout specifically for this toast
            LayoutInflater inflater = getLayoutInflater();
            View layout = inflater.inflate(R.layout.custom_toast_layout, null);

            // Retrieve the views inside the custom layout
            MaterialCardView toastCard = layout.findViewById(R.id.toastCard);
            ImageView toastIcon = layout.findViewById(R.id.toastIcon);
            TextView toastTitle = layout.findViewById(R.id.toastTitle);
            TextView toastMessage = layout.findViewById(R.id.toastMessage);

            // Apply the requested styling and text data
            int color = ContextCompat.getColor(requireContext(), colorRes);
            toastCard.setCardBackgroundColor(color);
            toastTitle.setText(title);
            toastMessage.setText(message);
            toastIcon.setImageResource(iconRes);

            // Build the Toast object and attach the custom layout
            Toast toast = new Toast(requireContext());
            toast.setDuration(Toast.LENGTH_LONG);
            toast.setView(layout);

            // Center the toast on the screen
            toast.setGravity(Gravity.CENTER, 0, 0);

            // Prepare the view for a custom fade-in and slide-up animation
            layout.setAlpha(0f);
            layout.setTranslationY(-50f);

            // Show the toast, then immediately animate the view inside it
            toast.show();
            layout.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .start();

        } catch (Exception e) {
            // Fallback: If custom layout inflation fails, show a simple text toast
            showSimpleToast(title + ": " + message);
        }
    }

    /**
     * Displays a standard Android Toast message.
     *
     * @param message The message to display.
     */
    private void showSimpleToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Orchestrates the sequential execution of entrance animations for all elements
     * on the home screen to create a cohesive loading experience.
     */
    private void startAnimations() {
        // Start counting animations on the stat cards immediately
        loadStaticStats();

        // Delay the entrance of the layout elements (cards and buttons) by 800ms
        // so the counting effect gets user attention first
        new Handler().postDelayed(this::animateContentEntrance, 800);
    }

    /**
     * Sets the static numerical data for the statistic cards and initiates
     * dynamic counting animations for numerical values.
     */
    private void loadStaticStats() {
        // Initialize first stat value and animate counting from 0 to 9
        statsValue1.setText("9");
        animateNumberCounter(statsValue1, 0, 9, 800);

        // Initialize second stat value and animate counting from 0 to 170
        statsValue2.setText("170");
        animateNumberCounter(statsValue2, 0, 170, 1000);

        // Third stat is a string, so no counting animation is needed
        statsValue3.setText("24/7");
    }

    /**
     * Staggers the fade-in and slide-up animations for the textual content,
     * statistics cards, and navigation buttons by using incrementing delays.
     */
    private void animateContentEntrance() {
        // Animate cards sequentially with 100ms offset
        animateStatsCardEntrance(statsCard1, 200);
        animateStatsCardEntrance(statsCard2, 300);
        animateStatsCardEntrance(statsCard3, 400);

        // Animate buttons sequentially with 100ms offset
        animateButtonEntrance(buttonAbout, 500);
        animateButtonEntrance(buttonFeatures, 600);
        animateButtonEntrance(buttonContact, 700);
    }

    /**
     * Animates a statistic MaterialCardView scaling and fading into view.
     *
     * @param card  The MaterialCardView to animate.
     * @param delay The delay in milliseconds before the animation begins.
     */
    private void animateStatsCardEntrance(MaterialCardView card, long delay) {
        // Reset properties so it appears hidden before the animation starts
        card.setScaleX(0f);
        card.setScaleY(0f);
        card.setAlpha(0f);

        // Animate towards full scale and full opacity
        card.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(500)
                .setStartDelay(delay)
                .setInterpolator(new AccelerateDecelerateInterpolator()) // Smooth easing
                .start();
    }

    /**
     * Animates a generic view (typically a button) fading in and sliding up into place.
     *
     * @param button The view to animate.
     * @param delay  The delay in milliseconds before the animation begins.
     */
    private void animateButtonEntrance(View button, long delay) {
        // Reset properties to be slightly pushed down and invisible
        button.setAlpha(0f);
        button.setTranslationY(50f);

        // Slide up to normal position while fading in
        button.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(delay)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    /**
     * Animates a TextView incrementally counting up from a starting value to an end value.
     *
     * @param textView The TextView to update with numerical values.
     * @param start    The integer starting value.
     * @param end      The integer ending value.
     * @param duration The duration of the counting animation in milliseconds.
     */
    private void animateNumberCounter(TextView textView, int start, int end, long duration) {
        // Create an animator that interpolates integers from start to end
        ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        // Update the TextView every time the animator tick changes the value
        animator.addUpdateListener(valueAnimator -> {
            // Format number with commas for readability (e.g., 1,000)
            String value = String.format(Locale.US, "%,d", (int)valueAnimator.getAnimatedValue());

            // Append a "+" sign if the final number is large (over 50), unless it's a fixed text like "24/7"
            textView.setText(value + (end > 50 && !value.contains("24/7") ? "+" : ""));
        });

        // Execute the counter animation
        animator.start();
    }

    /**
     * Applies a subtle press and release animation to a button before executing its intended action.
     *
     * @param v      The button view clicked.
     * @param action The runnable logic to fire after the animation.
     */
    private void animateModernButtonClick(View v, Runnable action) {
        // Step 1: Scale down simulating a physical press
        v.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(80)
                .withEndAction(() -> {
                    // Step 2: Scale back to normal simulating a release
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120)
                            .withEndAction(action) // Execute navigation or logic
                            .start();
                })
                .start();
    }

    /**
     * Fetches products using FirebaseHelper, shuffles them locally, and displays
     * up to 10 random products in a horizontal, scrollable popup window.
     */
    private void showRandomProductCarousel() {
        // Ensure fragment is safely attached before proceeding
        if (getContext() == null || getView() == null) return;

        // Use your existing helper to take advantage of its offline caching!
        // This fetches the full list of products.
        new FirebaseHelper().getAllProducts(new FirebaseHelper.ProductsCallback() {
            @Override
            public void onProductsLoaded(List<FirebaseHelper.Product> products) {
                // Double-check fragment attachment inside callback to avoid memory leaks/crashes
                if (!isAdded() || getView() == null) return;

                // Handle empty database case gracefully
                if (products == null || products.isEmpty()) {
                    showSimpleToast("No products available to display.");
                    return;
                }

                // 1. Shuffle the list to randomize the order of the products shown
                Collections.shuffle(products);

                // 2. Take only the first 10 items (or fewer if you don't have 10 products yet)
                // This prevents out-of-bounds exceptions.
                int limit = Math.min(10, products.size());
                List<FirebaseHelper.Product> randomTen = products.subList(0, limit);

                // 3. Setup the popup view by inflating the carousel layout
                View popupView = getLayoutInflater().inflate(R.layout.product_carousel_popup, null);
                LinearLayout horizontalContainer = popupView.findViewById(R.id.horizontalProductContainer);
                Button btnClose = popupView.findViewById(R.id.btnCloseCarousel);
                LayoutInflater productInflater = getLayoutInflater();

                // 4. Add the randomized products to the horizontal container inside the popup
                for (FirebaseHelper.Product product : randomTen) {
                    if (product.name != null && product.imageUrl != null) {
                        addProductCardToCarousel(productInflater, horizontalContainer, product.name, product.imageUrl);
                    }
                }

                // 5. Construct and configure the PopupWindow
                PopupWindow carouselPopup = new PopupWindow(
                        popupView,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true // Sets the popup to be focusable so touching outside or back button can dismiss it
                );

                // Add elevation for a shadow effect and set a solid background color
                carouselPopup.setElevation(20f);
                carouselPopup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));

                // Display the popup centered relative to the current fragment view
                carouselPopup.showAtLocation(requireView(), Gravity.CENTER, 0, 0);

                // Close the popup when the 'X' or Close button is clicked
                btnClose.setOnClickListener(v -> carouselPopup.dismiss());
            }

            @Override
            public void onError(String error) {
                // Handle Firebase fetch failure gracefully
                if (!isAdded()) return;
                showSimpleToast("Failed to load products: " + error);
            }
        });
    }

    /**
     * Inflates a generic product card, hides interactive buy elements, and adds it
     * sequentially into the horizontal carousel container.
     *
     * @param inflater  The LayoutInflater used to create the view.
     * @param container The parent LinearLayout hosting the carousel items.
     * @param name      The product name.
     * @param imageUrl  The URL of the product image.
     */
    private void addProductCardToCarousel(LayoutInflater inflater, LinearLayout container, String name, String imageUrl) {
        // Inflate a standard product row layout used elsewhere in the app
        View productView = inflater.inflate(R.layout.table_row_products, container, false);

        // Define fixed width and spacing constraints for carousel items
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                (int) getResources().getDimension(R.dimen.product_card_width),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd((int) getResources().getDimension(R.dimen.activity_horizontal_margin));
        productView.setLayoutParams(params);

        // Locate and load the product image using Picasso
        ImageView imageView = productView.findViewById(R.id.imageViewProduct);
        if (imageView != null) {
            try {
                Picasso.get()
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_store) // Show a default icon while loading
                        .error(R.drawable.ic_store)       // Show a default icon on failure
                        .fit()                            // Scale image to fit bounds
                        .centerCrop()                     // Crop to maintain aspect ratio
                        .into(imageView);
            } catch (Exception e) {
                // If Picasso completely fails or URL is corrupted, set a local fallback
                imageView.setImageResource(R.drawable.ic_store);
            }
        }

        // Set the product name text
        TextView textViewName = productView.findViewById(R.id.textViewName);
        if (textViewName != null) {
            textViewName.setText(name);
        }

        // Since this is just a showcase carousel, we remove/hide interaction buttons
        // like "price", "buy", and quantity adjusters to keep it clean.
        if (productView.findViewById(R.id.textViewPrice) != null) productView.findViewById(R.id.textViewPrice).setVisibility(View.GONE);
        if (productView.findViewById(R.id.buyButton) != null) productView.findViewById(R.id.buyButton).setVisibility(View.GONE);

        // Handle the quantity container logic (locating the parent layout of the minus button)
        View quantityContainer = productView.findViewById(R.id.minusButton).getParent() instanceof LinearLayout ?
                (View) productView.findViewById(R.id.minusButton).getParent() : null;
        if (quantityContainer != null) {
            quantityContainer.setVisibility(View.GONE);
        }

        // Finally, append the configured view into the horizontal scrolling container
        container.addView(productView);
    }

    /**
     * Called when the fragment's view is being destroyed. Removes queued handler callbacks
     * to ensure animations do not attempt to modify views that no longer exist,
     * which prevents memory leaks and NullPointerExceptions.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clear all pending runnables (like delayed entrance animations)
        animationHandler.removeCallbacksAndMessages(null);
    }
}