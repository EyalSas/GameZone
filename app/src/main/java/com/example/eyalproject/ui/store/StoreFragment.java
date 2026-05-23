package com.example.eyalproject.ui.store;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.eyalproject.FirebaseHelper;
import com.example.eyalproject.R;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * A fragment that displays the main store interface. It manages product fetching, categorization,
 * search filtering, and user interactions such as viewing product details and adding items to the cart.
 */
public class StoreFragment extends Fragment {
    private static final Map<String, List<FirebaseHelper.Product>> productCache = new LinkedHashMap<>();
    private static final Set<String> prewarmedFilters = new HashSet<>();
    private static FirebaseHelper fbHelper;
    private RecyclerView recyclerViewMain;
    private LinearLayout skeletonLayout;
    private Spinner spinnerFilter;
    private TextInputEditText editTextSearch;
    private PopupWindow activePopupWindow;
    private CategoryAdapter categoryAdapter;
    private ValueAnimator shimmerAnimator;
    private String selectedFilter = "All";
    private String currentSearchQuery = "";
    private float dp;
    private RequestOptions glideOptions;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Runnable searchRunnable = () -> {
        // Retrieve the currently cached list of products based on the active filter dropdown
        List<FirebaseHelper.Product> cached = productCache.get(selectedFilter);
        // If data exists, pass it through the filter logic to update the UI
        if (cached != null) filterAndRender(cached);
    };
    /**
     * Called to do initial creation of the fragment.
     * Retains the instance across configuration changes to prevent unnecessary data reloads.
     * @param savedInstanceState If the fragment is being re-created from a previous saved state, this is the state.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Instructs the OS to keep this fragment alive in memory when the device is rotated
        //noinspection deprecation
        setRetainInstance(true);
    }
    /**
     * Called to have the fragment instantiate its user interface view.
     * Initializes views, sets up adapters, configures glide, and triggers the initial product load.
     * @param inflater The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container If non-null, this is the parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state.
     * @return Return the View for the fragment's UI, or null.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the XML layout assigned to this fragment
        View root = inflater.inflate(R.layout.fragment_store, container, false);
        // Capture the device's display density to use for programmatic pixel calculations later
        dp = requireContext().getResources().getDisplayMetrics().density;
        // Initialize the database helper only if it hasn't been created yet to save memory
        if (fbHelper == null) fbHelper = new FirebaseHelper();
        // Pre-configure Glide image loading settings for consistent caching, cropping, and placeholder generation
        glideOptions = new RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL).override(240, 240)
                .centerCrop().placeholder(roundRect("#F0F0F0", 12)).error(roundRect("#FFCDD2", 12));
        // Execute all UI setup and data fetching sequences synchronously
        initViews(root);
        setupSpinner();
        setupSearch();
        loadProducts();
        return root;
    }
    /**
     * Called when the view previously created by onCreateView has been detached from the fragment.
     * Cleans up background threads, handlers, animations, and open popups to prevent memory leaks.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clear any pending search delays or UI updates from the main thread queue
        mainHandler.removeCallbacksAndMessages(null);
        // Dismiss the product detail popup if the user navigates away while it is open
        if (activePopupWindow != null && activePopupWindow.isShowing()) {
            activePopupWindow.dismiss();
            activePopupWindow = null;
        }
    }
    /**
     * Initializes the core XML UI components and sets up the primary vertical RecyclerView.
     * @param root The root view of the fragment's layout.
     */
    private void initViews(View root) {
        // Bind local variables to their respective views in the inflated layout
        recyclerViewMain = root.findViewById(R.id.recyclerViewMain);
        spinnerFilter = root.findViewById(R.id.spinnerFilter);
        editTextSearch = root.findViewById(R.id.editTextSearch);
        // Configure the main list to scroll vertically and attach an empty category adapter
        recyclerViewMain.setLayoutManager(new LinearLayoutManager(requireContext()));
        categoryAdapter = new CategoryAdapter(new ArrayList<>());
        recyclerViewMain.setAdapter(categoryAdapter);
    }

    /**
     * Configures the category filter spinner and attaches an item selection listener to trigger product reloads.
     */
    private void setupSpinner() {
        // Attempt to load the custom XML spinner styling, falling back to the Android default if it fails
        try {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(), R.array.filter_options, R.layout.spinner_item);
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
            spinnerFilter.setAdapter(adapter);
        } catch (Exception e) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(), R.array.filter_options, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerFilter.setAdapter(adapter);
        }
        // Attach a listener to detect when the user chooses a new category
        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                // Extract the string value of the selected item
                String newFilter = parent.getItemAtPosition(pos).toString();
                // Abort execution if the user selected the category that is already active
                if (newFilter.equals(selectedFilter)) return;
                // Update the state variable and fire the database load routine
                selectedFilter = newFilter;
                loadProducts();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    /**
     * Sets up the search input field with a text watcher. Triggers a delayed search routine to filter products.
     */
    private void setupSearch() {
        // Add a watcher to monitor real-time keystrokes in the search bar
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Normalize the search string to lowercase for case-insensitive matching
                currentSearchQuery = s.toString().trim().toLowerCase(Locale.ROOT);
                // Debounce the input by canceling the previous timer and starting a new 150ms delay
                mainHandler.removeCallbacks(searchRunnable);
                if (productCache.containsKey(selectedFilter)) {
                    mainHandler.postDelayed(searchRunnable, 150);
                }
            }
        });
    }
    /**
     * Initiates the loading of products from Firebase or retrieves them from the local cache if previously fetched.
     */
    private void loadProducts() {
        // Attempt to retrieve pre-fetched data from the cache map to bypass the network
        List<FirebaseHelper.Product> cached = productCache.get(selectedFilter);
        if (cached != null) {
            filterAndRender(cached);
            return;
        }
        // Define the callback behavior for when Firebase successfully returns data
        FirebaseHelper.ProductsCallback callback = new FirebaseHelper.ProductsCallback() {
            @Override
            public void onProductsLoaded(List<FirebaseHelper.Product> products) {
                if (!isAdded()) return; // Prevent crashes if fragment detached
                // Store the raw network response in the local cache dictionary
                productCache.put(selectedFilter, products);
                // Dispatch a background thread to begin downloading the product images
                prewarmImages(products);
                // Return to the main UI thread to hide the  and render the actual list
                mainHandler.post(() -> {
                    filterAndRender(products);
                });
            }
            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                // Return to main thread to show an error notification
                mainHandler.post(() -> {
                    showSnackbar("Error loading products: " + error);
                });
            }
        };
        // Route the query to the correct database method based on the selected dropdown filter
        if ("All".equals(selectedFilter)) {
            fbHelper.getAllProducts(callback);
        } else {
            String key = selectedFilter.toUpperCase(Locale.ROOT).replace(" ", "_");
            fbHelper.getProductsByType(key, callback);
        }
    }
    /**
     * Preloads product images in the background to ensure smoother scrolling when the user interacts with the lists.
     * @param products The list of products whose images should be preloaded.
     */
    private void prewarmImages(List<FirebaseHelper.Product> products) {
        // Check the tracking set to ensure we don't dispatch duplicate preload commands for this filter
        if (prewarmedFilters.contains(selectedFilter)) return;
        prewarmedFilters.add(selectedFilter);
        // Offload the image fetching loop to the background executor to keep the UI fluid
        backgroundExecutor.execute(() -> {
            for (FirebaseHelper.Product p : products) {
                // Skip entries that possess broken or null image references
                if (p.imageUrl == null || p.imageUrl.isEmpty()) continue;
                // Instruct Glide to download and cache the image at the specified resolution without attaching it to a view
                try { Glide.with(requireContext()).load(p.imageUrl).apply(glideOptions).preload(240, 240);
                } catch (Exception ignored) {}
            }
        });
    }
    /**
     * Filters a list of products based on the current search query and organizes them into categorized groupings.
     * @param source The unfiltered list of products to process.
     */
    private void filterAndRender(List<FirebaseHelper.Product> source) {
        if (source == null || !isAdded()) return;
        String query = currentSearchQuery;
        // Utilize a LinkedHashMap to preserve the insertion order of the categories
        Map<String, List<FirebaseHelper.Product>> grouped = new LinkedHashMap<>();
        // Iterate over the raw product list, filtering by the search string and binning by product type
        for (FirebaseHelper.Product p : source) {
            if (query.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(query)) {
                String type = (p.type != null) ? p.type : "OTHER";
                grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(p);
            }
        }
        // If the search yielded zero results, clear the adapter and notify the user
        if (grouped.isEmpty()) {
            categoryAdapter.updateData(new ArrayList<>());
            showSnackbar("No products found.");
            return;
        }
        // Reformat the dictionary map into a clean list of CategoryData objects for the adapter
        List<CategoryData> newCategories = new ArrayList<>();
        for (Map.Entry<String, List<FirebaseHelper.Product>> entry : grouped.entrySet()) {
            // Clean the raw database string (e.g., "BOARD_GAMES" becomes "Board games")
            String display = entry.getKey().replace('_', ' ').toLowerCase(Locale.ROOT);
            display = Character.toUpperCase(display.charAt(0)) + display.substring(1);
            newCategories.add(new CategoryData(display, entry.getValue()));
        }
        // Dispatch the finalized data structure to the UI adapter
        categoryAdapter.updateData(newCategories);
    }
    /**
     * Initiates the process of adding a specified quantity of a product to the user's cart.
     * @param name The product name.
     * @param price The unit price of the product.
     * @param qty The quantity to add to the cart.
     */
    private void purchaseProduct(String name, double price, int qty) {
        // Enforce user authentication before communicating with the database
        if (fbHelper.getCurrentUserId() == null) {
            showSnackbar("Error: User session not found. Please log in.");
            return;
        }
        // Send payload to Firebase database helper and show confirmation popup
        fbHelper.addToCart(name, price, qty);
        Toast.makeText(getContext(), qty + " × " + name + " added to cart!", Toast.LENGTH_SHORT).show();
    }
    /**
     * Displays a centered popup window featuring an enlarged product image and details.
     * @param name The product name.
     * @param imageUrl The URL of the product image.
     * @param price The product price.
     */
    private void showProductPopup(String name, String imageUrl, double price) {
        if (getContext() == null || getView() == null) return;
        // Automatically dismiss any already-open popup to prevent overlapping instances
        if (activePopupWindow != null && activePopupWindow.isShowing()) activePopupWindow.dismiss();
        // Inflate the designated XML layout for the popup window
        View popupView = getLayoutInflater().inflate(R.layout.product_details_popup, null);
        // Populate text components with the passed product data
        ((TextView) popupView.findViewById(R.id.textViewPopupName)).setText(name);
        ((TextView) popupView.findViewById(R.id.textViewPopupPrice)).setText(String.format(Locale.ROOT, "$%.2f", price));
        // Retrieve the image view and invoke Glide to load the high-resolution texture
        ImageView img = popupView.findViewById(R.id.imageViewPopupProduct);
        try { Glide.with(requireContext()).load(imageUrl).diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(roundRect("#F0F0F0", 12)).error(roundRect("#FFCDD2", 12)).centerCrop().into(img);
        } catch (Exception e) { img.setBackgroundColor(Color.LTGRAY); }
        // Build the physical PopupWindow object, applying elevation and a transparent background for rounded corners
        activePopupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        activePopupWindow.setElevation(100);
        activePopupWindow.setOutsideTouchable(true);
        activePopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // Command the window to present itself in the exact center of the screen
        activePopupWindow.showAtLocation(requireView(), Gravity.CENTER, 0, 0);
        // Bind the close button click event to dismiss the window
        popupView.findViewById(R.id.btnClosePopup).setOnClickListener(v -> activePopupWindow.dismiss());
    }
    /**     * Displays a brief Snackbar message to the user at the bottom of the view.
     * @param message The text message to display.
     */
    private void showSnackbar(String message) {
        // Ensure the view is attached before invoking the Material Design snackbar component
        if (getView() != null) {
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).setAction("OK", v -> {}).show();
        }
    }
    /**
     * Extracts an integer quantity from the provided TextView securely.
     * @param counter The TextView containing the quantity value.
     * @return The parsed integer quantity, or 0 if parsing fails.
     */
    private int getSafeQuantity(TextView counter) {
        // Attempt to parse string to integer, catching exceptions to prevent crashes on invalid input
        try { return Integer.parseInt(counter.getText().toString().trim());
        } catch (NumberFormatException e) { return 0; }
    }
    /**
     * Creates a rounded rectangle drawable dynamically for use as view backgrounds.
     * @param hex The hex color string.
     * @param radiusDp The corner radius in density-independent pixels.
     * @return A styled GradientDrawable.
     */
    private Drawable roundRect(String hex, int radiusDp) {
        // Instantiate a gradient shape object, assign rectangle properties, set color, and apply scaled corner curves
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(Color.parseColor(hex));
        d.setCornerRadius(radiusDp * dp);
        return d;
    }
    /**
     * Converts a density-independent pixel (dp) value into an exact pixel count.
     * @param dp The dimension in dp.
     * @return The rounded integer pixel equivalent.
     */
    private int px(int dp) { return Math.round(dp * this.dp); }
    /**
     * A simple data object representing a categorized group of products.
     */
    private static class CategoryData {
        String name;
        List<FirebaseHelper.Product> products;
        /**
         * Constructs a new CategoryData instance.
         * @param name The display name of the category.
         * @param products The list of products belonging to this category.
         */
        CategoryData(String name, List<FirebaseHelper.Product> products) {
            this.name = name;
            this.products = products;
        }
    }
    /**
     * An adapter for the main vertical RecyclerView, responsible for displaying category headers and initializing nested lists.
     */
    private class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {
        private List<CategoryData> categories;
        /**
         * Constructs a CategoryAdapter.
         * @param categories The initial list of category data to display.
         */
        CategoryAdapter(List<CategoryData> categories) { this.categories = categories; }
        /**
         * Updates the adapter's underlying data set and refreshes the layout.
         * @param newData The new list of category data.
         */
        void updateData(List<CategoryData> newData) {
            this.categories = newData;
            notifyDataSetChanged();
        }
        @NonNull
        @Override
        public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Build the outer vertical layout combining the category text header and horizontal recycler track
            LinearLayout layout = new LinearLayout(parent.getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            // Assemble and append header string
            TextView header = new TextView(parent.getContext());
            header.setTextSize(20);
            header.setTypeface(Typeface.DEFAULT_BOLD);
            header.setTextColor(Color.parseColor("#1976D2"));
            header.setPadding(px(16), px(24), px(16), px(8));
            layout.addView(header);
            // Assemble and append inner horizontal list framework
            RecyclerView horizontalRecycler = new RecyclerView(parent.getContext());
            horizontalRecycler.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            horizontalRecycler.setClipToPadding(false);
            horizontalRecycler.setPadding(px(8), 0, px(8), 0);
            horizontalRecycler.setLayoutManager(new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
            layout.addView(horizontalRecycler);
            return new CategoryViewHolder(layout, header, horizontalRecycler);
        }
        @Override
        public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
            // Retrieve logical block data, apply string to header, and construct a new inner adapter for horizontal scrolling
            CategoryData data = categories.get(position);
            holder.title.setText(data.name);
            holder.productRecycler.setAdapter(new ProductAdapter(data.products));
        }
        @Override
        public int getItemCount() { return categories.size(); }
        /**
         * View holder pattern implementation for category rows.
         */
        class CategoryViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            RecyclerView productRecycler;
            /**
             * Constructs a CategoryViewHolder.
             * @param itemView The parent layout containing the category UI.
             * @param title The TextView displaying the category name.
             * @param productRecycler The nested RecyclerView handling the product list.
             */
            CategoryViewHolder(View itemView, TextView title, RecyclerView productRecycler) {
                super(itemView);
                this.title = title;
                this.productRecycler = productRecycler;
            }
        }
    }
    /**
     * An adapter for the nested horizontal RecyclerViews, managing individual product cards, quantities, and purchase actions.
     */
    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {
        private final List<FirebaseHelper.Product> products;
        /**
         * Constructs a ProductAdapter.
         * @param products The list of products to populate within the nested list.
         */
        ProductAdapter(List<FirebaseHelper.Product> products) { this.products = products; }
        @NonNull
        @Override
        public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Inflate individual product item structure from static XML
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.table_row_products, parent, false);
            return new ProductViewHolder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
            // Extract model data and map into view holder components
            FirebaseHelper.Product p = products.get(position);
            holder.name.setText(p.name);
            holder.price.setText(String.format(Locale.ROOT, "$%.2f", p.price));
            holder.counter.setText("0");
            // Render network image using Glide library, assigning a default gray block upon failure
            try { Glide.with(StoreFragment.this).load(p.imageUrl).apply(glideOptions).into(holder.image);
            } catch (Exception e) { holder.image.setBackgroundColor(Color.LTGRAY); }
            // Delegate interactions for detail inspection, counter decrement, counter increment, and final purchase submission
            holder.image.setOnClickListener(v -> showProductPopup(p.name, p.imageUrl, p.price));
            holder.btnMinus.setOnClickListener(v -> {
                int n = getSafeQuantity(holder.counter);
                if (n > 0) holder.counter.setText(String.valueOf(n - 1));
            });
            holder.btnPlus.setOnClickListener(v -> holder.counter.setText(String.valueOf(getSafeQuantity(holder.counter) + 1)));
            holder.btnBuy.setOnClickListener(v -> {
                int n = getSafeQuantity(holder.counter);
                if (n > 0) {
                    purchaseProduct(p.name, p.price, n);
                    holder.counter.setText("0");
                } else { showSnackbar("Please specify a quantity greater than zero"); }
            });
        }
        @Override
        public int getItemCount() { return products.size(); }
        /**
         * View holder pattern implementation for individual product items.
         */
        class ProductViewHolder extends RecyclerView.ViewHolder {
            ImageView image;
            TextView name, price, counter;
            Button btnMinus, btnPlus, btnBuy;
            /**
             * Constructs a ProductViewHolder mapping all sub-components.
             * @param view The root view of the product card layout.
             */
            ProductViewHolder(View view) {
                super(view);
                image = view.findViewById(R.id.imageViewProduct);
                name = view.findViewById(R.id.textViewName);
                price = view.findViewById(R.id.textViewPrice);
                counter = view.findViewById(R.id.textViewCount);
                btnMinus = view.findViewById(R.id.minusButton);
                btnPlus = view.findViewById(R.id.plusButton);
                btnBuy = view.findViewById(R.id.buyButton);

            }
        }
    }
}