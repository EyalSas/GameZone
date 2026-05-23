package com.example.eyalproject.ui.service;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.eyalproject.MainActivity;
import com.example.eyalproject.R;
import com.example.eyalproject.databinding.FragmentServiceBinding;
import com.example.eyalproject.ui.cart.CartReminderReceiver;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * A fragment responsible for managing user service requests.
 * It provides a dual-role interface:
 * - Standard Users: Can submit new service requests and view their own request history and statistics.
 * - Administrators: Can view all system-wide service requests and mark pending requests as completed.
 */
public class ServiceFragment extends Fragment {

    private FragmentServiceBinding binding;
    private String username;

    /**
     * The designated username string that grants administrative privileges within this fragment.
     */
    private static final String ADMIN_USERNAME = "admin";

    /**
     * Called to have the fragment instantiate its user interface view.
     * Initializes the binding, retrieves the current user's username from the hosting activity,
     * and triggers the initial UI setup and data loading.
     *
     * @param inflater           The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container          If non-null, this is the parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state.
     * @return Return the View for the fragment's UI.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout specifically for this fragment using ViewBinding
        binding = FragmentServiceBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        // Securely retrieve the logged-in username from the parent MainActivity
        if (getActivity() != null) {
            username = ((MainActivity) getActivity()).getUsername();
        }
        // Set up the UI elements based on user role and fetch data from Firebase
        initializeUI();
        loadServicesFromDatabase();
        return root;
    }

    /**
     * Configures the initial visibility and interaction states of the UI components
     * based on whether the current user is an administrator or a standard user.
     */
    private void initializeUI() {
        // Map local variables to UI components for quick access
        Button addServiceBtn = binding.btnAddService;
        EditText serviceNameEt = binding.editTextServiceName;
        LinearLayout statsLayout = binding.statsOverviewLayout;
        TextView textrequest=binding.textRequest;
        // Check if the current user matches the hardcoded admin credentials
        boolean isAdmin = ADMIN_USERNAME.equalsIgnoreCase(username);

        if (isAdmin) {
            // Admins do not submit services or track personal stats, so hide these elements
            textrequest.setVisibility(View.GONE);
            addServiceBtn.setVisibility(View.GONE);
            serviceNameEt.setVisibility(View.GONE);
            statsLayout.setVisibility(View.GONE);
            binding.servicesListTitle.setText("All User Service Requests (Admin View)");
        } else {
            // Standard users see the input fields, submit button, and personal stats
            addServiceBtn.setVisibility(View.VISIBLE);
            serviceNameEt.setVisibility(View.VISIBLE);
            statsLayout.setVisibility(View.VISIBLE);
            binding.servicesListTitle.setText("Your Service Requests");
            // Attach the click listener to handle submitting a new service request
            addServiceBtn.setOnClickListener(v -> {
                String serviceName = serviceNameEt.getText().toString().trim();
                if (!serviceName.isEmpty()) {
                    addService(serviceName);
                    serviceNameEt.setText(""); // Clear the input field after submission
                } else {
                    Toast.makeText(getContext(), "Please enter a service name", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Submits a new service request to the Firebase Firestore database under the current user's UID.
     * Upon successful insertion, broadcasts an intent to trigger a local notification
     * and refreshes the fragment's data.
     *
     * @param serviceName The name or description of the requested service.
     */
    private void addService(String serviceName) {
        // Ensure the user is actually authenticated before proceeding
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        // Construct the payload to save in Firestore
        Map<String, Object> serviceRequest = new HashMap<>();
        serviceRequest.put("serviceName", serviceName);
        serviceRequest.put("ownerUid", uid);
        serviceRequest.put("ownerUsername", username);
        serviceRequest.put("status", "waiting"); // All new requests start as waiting
        serviceRequest.put("timestamp", System.currentTimeMillis());

        // Push the new record to the "services" collection
        FirebaseFirestore.getInstance().collection("services").add(serviceRequest)
                .addOnSuccessListener(documentReference -> {
                    // Prevent crashes if the user navigated away before the callback finishes
                    if (!isAdded() || getContext() == null) return;
                    // Trigger a local broadcast to show a system notification about the new request
                    Intent intent = new Intent(getContext(), CartReminderReceiver.class);
                    intent.setAction("NEW_SERVICE_REQUEST");
                    intent.putExtra("message", username + " has requested a new service: " + serviceName);
                    intent.putExtra("title", "🔔 New Service Request!");
                    getContext().sendBroadcast(intent);

                    Toast.makeText(getContext(), "Service added successfully (Waiting)", Toast.LENGTH_SHORT).show();
                    // Refresh the view so the user sees their new request immediately
                    loadServicesFromDatabase();
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Failed to add service", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Orchestrates the retrieval of data from the database by calling the specific
     * methods responsible for updating statistics and loading the list of service items.
     */
    private void loadServicesFromDatabase() {
        // Update the numerical counters at the top (if applicable) and populate the list below
        updateServiceCounts();
        loadServiceItems();
    }


    /**
     * Asynchronously fetches the user's service requests from Firestore to calculate
     * the total number of 'waiting' and 'completed' requests, and updates the UI statistic cards.
     * This method bypasses execution if the current user is an admin, as admins do not track personal stats.
     */
    private void updateServiceCounts() {
        // Admins and unauthenticated users do not have personal statistics to display
        if (ADMIN_USERNAME.equalsIgnoreCase(username) || FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        // Query only the current user's documents
        FirebaseFirestore.getInstance().collection("services")
                .whereEqualTo("ownerUid", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded() || getContext() == null) return;
                    int waiting = 0;
                    int completed = 0;
                    // Iterate through the results and tally the statuses
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String status = doc.getString("status");
                        if ("waiting".equals(status)) waiting++;
                        if ("completed".equals(status)) completed++;
                    }
                    // Extract the text views embedded inside the statistical card layouts
                    View waitingCard = binding.cardWaiting.getRoot();
                    View completedCard = binding.cardCompleted.getRoot();
                    TextView waitingCount = waitingCard.findViewById(R.id.waitingCount);
                    TextView completedCount = completedCard.findViewById(R.id.completedCount);
                    // Apply the final tallied numbers to the UI
                    waitingCount.setText(String.valueOf(waiting));
                    completedCount.setText(String.valueOf(completed));
                });
    }

    /**
     * Retrieves service request documents from Firestore and dynamically populates the UI container.
     * Admins receive a chronological list of all requests, while standard users receive only their own.
     */
    private void loadServiceItems() {
        LinearLayout servicesContainer = binding.servicesContainer;
        // Clear any existing cards to prevent duplicates when reloading
        servicesContainer.removeAllViews();
        boolean isAdmin = ADMIN_USERNAME.equalsIgnoreCase(username);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query query;
        // Determine the query scope based on user role
        if (isAdmin) {
            // Admins see everything, oldest to newest
            query = db.collection("services").orderBy("timestamp", Query.Direction.ASCENDING);
        } else {
            // Regular users only see items attached to their specific UID
            if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            query = db.collection("services").whereEqualTo("ownerUid", uid).orderBy("timestamp", Query.Direction.ASCENDING);
        }

        query.get().addOnSuccessListener(querySnapshot -> {
            if (!isAdded() || getContext() == null) return;
            // Double clear just in case asynchronous timing caused an overlap
            servicesContainer.removeAllViews();
            // Loop through all fetched documents and generate visual cards for each
            for (QueryDocumentSnapshot doc : querySnapshot) {
                String docId = doc.getId();
                String serviceName = doc.getString("serviceName");
                String status = doc.getString("status");
                String ownerUsername = doc.getString("ownerUsername");
                // Programmatically build the view block and attach it to the scrollable container
                View serviceCard = createServiceCard(docId, serviceName, status, ownerUsername, isAdmin);
                servicesContainer.addView(serviceCard);
            }
        }).addOnFailureListener(e -> {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Failed to load services", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Converts a value in density-independent pixels (dp) to absolute pixels (px).
     *
     * @param dp The dimension in dp to convert.
     * @return The calculated dimension in pixels.
     */
    private int dpToPx(int dp) {
        // Uses the device's exact display metrics to scale layout sizes appropriately
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    /**
     * Programmatically constructs a visual card representing a single service request.
     * If the user is an admin, it includes the requester's name and an interactive button
     * to mark pending requests as completed.
     *
     * @param docId         The unique Firestore document ID of the service request.
     * @param serviceName   The name or description of the requested service.
     * @param status        The current status of the request ('waiting' or 'completed').
     * @param ownerUsername The username of the user who submitted the request.
     * @param isAdmin       A boolean indicating if the viewing user has administrative privileges.
     * @return The constructed View representing the service card.
     */
    private View createServiceCard(String docId, String serviceName, String status, String ownerUsername, boolean isAdmin) {
        Context context = getContext();
        // Setup the root container for the individual card
        LinearLayout rootLayout = new LinearLayout(context);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, dpToPx(10));
        rootLayout.setLayoutParams(layoutParams);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        rootLayout.setBackgroundColor(Color.parseColor("#2C2C2C")); // Dark gray background
        // Fallbacks for corrupt or missing data
        if (serviceName == null) serviceName = "Unknown Service";
        if (status == null) status = "unknown";
        // Determine the text color based on the status string
        int statusColor = Color.parseColor("#FFFFFF");
        if ("completed".equalsIgnoreCase(status)) {
            statusColor = Color.parseColor("#4CAF50"); // Green
        } else if ("waiting".equalsIgnoreCase(status)) {
            statusColor = Color.parseColor("#FF6347"); //  Red
        }
        // Build and add the service name header(title)
        TextView nameTextView = new TextView(context);
        nameTextView.setText(serviceName);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        nameTextView.setTextColor(Color.WHITE);
        nameTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        rootLayout.addView(nameTextView);
        // If admin, display who requested it so they know who to assist
        if (isAdmin) {
            TextView ownerTextView = new TextView(context);
            ownerTextView.setText("Requested by: " + (ownerUsername != null ? ownerUsername : "Unknown"));
            ownerTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            ownerTextView.setTextColor(Color.parseColor("#AAAAAA"));
            rootLayout.addView(ownerTextView);
        }
        // Create a horizontal row to hold the status label and potentially the admin button
        LinearLayout statusRow = new LinearLayout(context);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dpToPx(8);
        statusRow.setLayoutParams(rowParams);
        // Build the "Status:" prefix text
        TextView statusLabel = new TextView(context);
        statusLabel.setText("Status: ");
        statusLabel.setTextColor(Color.parseColor("#999999"));
        statusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusRow.addView(statusLabel);
        // Build the actual status value (e.g., WAITING or COMPLETED) colored accordingly
        TextView statusTextView = new TextView(context);
        statusTextView.setText(status.toUpperCase());
        statusTextView.setTextColor(statusColor);
        statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        statusRow.addView(statusTextView);
        // If an admin is viewing a pending request, inject the action button
        if (isAdmin && !"completed".equalsIgnoreCase(status)) {
            Button adminButton = new Button(context);
            adminButton.setText("Complete");
            adminButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            adminButton.setBackgroundColor(Color.parseColor("#4CAF50"));
            adminButton.setTextColor(Color.WHITE);
            // Push the button to the right side using layout weight
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, dpToPx(35));
            buttonParams.leftMargin = dpToPx(16);
            buttonParams.weight = 1.0f;
            adminButton.setLayoutParams(buttonParams);
            // Capture final variables for use inside the lambda callback
            final String finalServiceName = serviceName;
            final String finalOwnerUsername = ownerUsername;
            // Execute the status update when clicked
            adminButton.setOnClickListener(v -> handleAdminAction(docId, finalServiceName, finalOwnerUsername));
            statusRow.addView(adminButton);
        }
        // Finalize the layout hierarchy
        rootLayout.addView(statusRow);
        return rootLayout;
    }

    /**
     * Executes a database update to change the status of a specific service request to 'completed'.
     * Reloads the dataset upon a successful transaction.
     *
     * @param docId         The unique Firestore document ID of the service request to modify.
     * @param serviceName   The name of the service (used for user feedback).
     * @param ownerUsername The name of the requesting user (used for user feedback).
     */
    private void handleAdminAction(String docId, String serviceName, String ownerUsername) {
        // Target the specific document in Firestore and update just the "status" field
        FirebaseFirestore.getInstance().collection("services").document(docId)
                .update("status", "completed")
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded() || getContext() == null) return;
                    // Automatically refresh the UI to show the new green "COMPLETED" status
                    loadServicesFromDatabase();
                    Toast.makeText(getContext(), serviceName + " status set to completed for " + ownerUsername, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Failed to update service.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Called when the fragment becomes visible to the user. Forces a refresh of the
     * service data to ensure the UI is in sync with the database.
     */
    @Override
    public void onResume() {
        super.onResume();
        // If returning to this tab, fetch the latest data to avoid stale views
        if (username != null) {
            loadServicesFromDatabase();
        }
    }

    /**
     * Called when the view previously created by onCreateView has been detached from the fragment.
     * Cleans up the view binding to prevent memory leaks.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Nullify the binding reference allowing the garbage collector to free up the view memory
        binding = null;
    }
}