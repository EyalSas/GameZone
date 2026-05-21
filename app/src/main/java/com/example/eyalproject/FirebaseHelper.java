package com.example.eyalproject;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A helper class that centralizes all interactions with Firebase Authentication
 * and Firebase Firestore. It provides a singleton instance for Firestore to
 * manage offline persistence and provides abstracted methods for user management,
 * product retrieval, cart operations, and checkout history.
 */
public class FirebaseHelper {

    private static final String TAG = "FirebaseHelper";
    private static FirebaseFirestore dbInstance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    /**
     * Initializes the FirebaseHelper by acquiring the FirebaseAuth instance
     * and the singleton FirebaseFirestore instance.
     */
    public FirebaseHelper() {
        auth = FirebaseAuth.getInstance();
        db   = getDb();
    }

    /**
     * Retrieves a singleton instance of FirebaseFirestore configured with offline persistence.
     *
     * @return The configured FirebaseFirestore singleton instance.
     */
    private static synchronized FirebaseFirestore getDb() {
        if (dbInstance == null) {
            dbInstance = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            dbInstance.setFirestoreSettings(settings);
        }
        return dbInstance;
    }

    /**
     * Callback interface for authentication operations.
     */
    public interface AuthCallback {
        /**
         * Called when the authentication process succeeds.
         *
         * @param username The username of the successfully authenticated user.
         */
        void onSuccess(String username);

        /**
         * Called when the authentication process fails.
         *
         * @param error A description of the error that occurred.
         */
        void onFailure(String error);
    }

    /**
     * Registers a new user using their email and password, and saves their username to Firestore.
     * Prevents registration if the username is already taken.
     *
     * @param username The desired username.
     * @param email    The user's email address.
     * @param password The user's password.
     * @param callback The callback to trigger upon success or failure.
     */
    public void registerUser(String username, String email, String password, AuthCallback callback) {
        db.collection("users").whereEqualTo("username", username).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        callback.onFailure("Username is already taken. Please choose another.");
                        return;
                    }
                    auth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener(result -> {
                                FirebaseUser user = result.getUser();
                                if (user == null) return;
                                Map<String, Object> data = new HashMap<>();
                                data.put("username", username);
                                data.put("email", email);
                                db.collection("users").document(user.getUid()).set(data)
                                        .addOnSuccessListener(v -> callback.onSuccess(username))
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Profile save failed, Auth ok");
                                            callback.onSuccess(username);
                                        });
                            })
                            .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onFailure("DB Error: " + e.getMessage()));
    }

    /**
     * Logs in an existing user by their username. This method looks up the associated
     * email address in Firestore first, then authenticates with Firebase Auth.
     *
     * @param username The user's username.
     * @param password The user's password.
     * @param callback The callback to trigger upon success or failure.
     */
    public void loginUserByUsername(String username, String password, AuthCallback callback) {
        db.collection("users").whereEqualTo("username", username).get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        callback.onFailure("Username not found.");
                        return;
                    }
                    String email = snap.getDocuments().get(0).getString("email");
                    if (email == null) {
                        callback.onFailure("User data is corrupted.");
                        return;
                    }
                    auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener(r -> callback.onSuccess(username))
                            .addOnFailureListener(e -> callback.onFailure("Invalid password."));
                })
                .addOnFailureListener(e -> callback.onFailure("Database connection failed."));
    }

    /**
     * Retrieves the Unique ID of the currently authenticated user.
     *
     * @return The current user's UID, or null if no user is logged in.
     */
    public String getCurrentUserId() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    /**
     * Represents a product available in the store.
     */
    public static class Product {
        public String name;
        public double price;
        public String imageUrl;
        public String type;
    }

    /**
     * Callback interface for retrieving product data.
     */
    public interface ProductsCallback {
        /**
         * Called when the list of products is successfully loaded.
         *
         * @param products The loaded list of products.
         */
        void onProductsLoaded(List<Product> products);

        /**
         * Called when an error occurs during product retrieval.
         *
         * @param error A description of the error.
         */
        void onError(String error);
    }

    /**
     * Retrieves all products from Firestore using a cache-first strategy.
     * If the cache is empty, it falls back to fetching from the network.
     *
     * @param callback The callback to return the list of products or an error.
     */
    public void getAllProducts(ProductsCallback callback) {
        db.collection("products")
                .get(Source.CACHE)
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        callback.onProductsLoaded(parseProducts(snap));
                    } else {
                        fetchAllProductsFromNetwork(callback);
                    }
                })
                .addOnFailureListener(e -> fetchAllProductsFromNetwork(callback));
    }

    /**
     * Fetches all products directly from the Firestore network.
     *
     * @param callback The callback to return the list of products or an error.
     */
    private void fetchAllProductsFromNetwork(ProductsCallback callback) {
        db.collection("products")
                .get(Source.SERVER)
                .addOnSuccessListener(snap -> callback.onProductsLoaded(parseProducts(snap)))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Retrieves products filtered by a specific category type using a cache-first strategy.
     *
     * @param productType The category type to filter by.
     * @param callback    The callback to return the list of filtered products or an error.
     */
    public void getProductsByType(String productType, ProductsCallback callback) {
        db.collection("products")
                .whereEqualTo("type", productType)
                .get(Source.CACHE)
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        callback.onProductsLoaded(parseProducts(snap));
                    } else {
                        fetchProductsByTypeFromNetwork(productType, callback);
                    }
                })
                .addOnFailureListener(e -> fetchProductsByTypeFromNetwork(productType, callback));
    }

    /**
     * Fetches products filtered by a specific category type directly from the Firestore network.
     *
     * @param productType The category type to filter by.
     * @param callback    The callback to return the list of filtered products or an error.
     */
    private void fetchProductsByTypeFromNetwork(String productType, ProductsCallback callback) {
        db.collection("products")
                .whereEqualTo("type", productType)
                .get(Source.SERVER)
                .addOnSuccessListener(snap -> callback.onProductsLoaded(parseProducts(snap)))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Parses a Firestore QuerySnapshot into a list of Product objects.
     *
     * @param snap The QuerySnapshot returned from Firestore.
     * @return A list of populated Product objects.
     */
    private List<Product> parseProducts(com.google.firebase.firestore.QuerySnapshot snap) {
        List<Product> list = new ArrayList<>(snap.size());
        for (QueryDocumentSnapshot doc : snap) {
            Product p  = new Product();
            p.name     = doc.getString("name");
            p.price    = doc.getDouble("price") != null ? doc.getDouble("price") : 0.0;
            p.imageUrl = doc.getString("imageUrl");
            p.type     = doc.getString("type");
            list.add(p);
        }
        return list;
    }

    /**
     * Uploads a single product record to the Firestore database.
     * Replaces slashes in the product name to ensure valid document IDs.
     *
     * @param name     The product name.
     * @param price    The product price.
     * @param imageUrl The URL of the product image.
     * @param type     The product category type.
     */
    public void uploadSingleProduct(String name, double price, String imageUrl, String type) {
        Map<String, Object> product = new HashMap<>();
        product.put("name", name);
        product.put("price", price);
        product.put("imageUrl", imageUrl);
        product.put("type", type);
        String safeId = name.replace("/", "-");
        db.collection("products").document(safeId).set(product);
    }

    /**
     * Generic callback interface for general actions.
     */
    public interface ActionCallback {
        /**
         * Called when the action successfully completes.
         */
        void onSuccess();

        /**
         * Called when the action fails.
         *
         * @param error A description of the error.
         */
        void onFailure(String error);
    }

    /**
     * Represents a single item stored within a user's cart.
     */
    public static class CartItem {
        public String documentId;
        public String productName;
        public double price;
        public int quantity;
    }

    /**
     * Callback interface for cart retrieval operations.
     */
    public interface CartCallback {
        /**
         * Called when the user's cart items are successfully loaded.
         *
         * @param items    The list of items in the cart.
         * @param totalSum The total calculated price of all items in the cart.
         */
        void onCartLoaded(List<CartItem> items, double totalSum);

        /**
         * Called when an error occurs during cart retrieval.
         *
         * @param error A description of the error.
         */
        void onError(String error);
    }

    /**
     * Adds a specific quantity of a product to the currently authenticated user's cart.
     *
     * @param productName The name of the product being added.
     * @param price       The unit price of the product.
     * @param quantity    The amount of the product being added.
     */
    public void addToCart(String productName, double price, int quantity) {
        String uid = getCurrentUserId();
        if (uid == null) return;

        Map<String, Object> item = new HashMap<>();
        item.put("productName", productName);
        item.put("price", price);
        item.put("quantity", quantity);
        item.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(uid).collection("cart").add(item)
                .addOnSuccessListener(ref -> Log.d(TAG, "Cart item added"))
                .addOnFailureListener(e -> Log.e(TAG, "Cart add failed", e));
    }

    /**
     * Retrieves all items currently in the authenticated user's cart.
     *
     * @param callback The callback returning the cart items and the calculated total sum.
     */
    public void getCartItems(CartCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onError("User not logged in"); return; }

        db.collection("users").document(uid).collection("cart")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    List<CartItem> items = new ArrayList<>(snap.size());
                    double total = 0;
                    for (QueryDocumentSnapshot doc : snap) {
                        CartItem ci  = new CartItem();
                        ci.documentId   = doc.getId();
                        ci.productName  = doc.getString("productName");
                        ci.price        = doc.getDouble("price") != null ? doc.getDouble("price") : 0.0;
                        ci.quantity     = doc.getLong("quantity") != null
                                ? doc.getLong("quantity").intValue() : 1;
                        total += ci.price * ci.quantity;
                        items.add(ci);
                    }
                    callback.onCartLoaded(items, total);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Deletes a specific item from the user's cart based on its Firestore document ID.
     *
     * @param documentId The Firestore document ID of the cart item.
     * @param callback   The callback indicating success or failure of the deletion.
     */
    public void deleteCartItem(String documentId, ActionCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onFailure("User not logged in"); return; }

        db.collection("users").document(uid).collection("cart").document(documentId)
                .delete()
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Callback interface for returning the count of items in a cart.
     */
    public interface CartCountCallback {
        /**
         * Called with the total number of items in the cart.
         *
         * @param count The number of distinct cart items.
         */
        void onCountRetrieved(int count);
    }

    /**
     * Retrieves the total count of distinct items in the user's cart.
     *
     * @param callback The callback that returns the total count.
     */
    public void getCartItemCount(CartCountCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onCountRetrieved(0); return; }

        db.collection("users").document(uid).collection("cart").get()
                .addOnSuccessListener(snap -> callback.onCountRetrieved(snap.size()))
                .addOnFailureListener(e -> callback.onCountRetrieved(0));
    }

    /**
     * Callback interface for the checkout process.
     */
    public interface CheckoutCallback {
        /**
         * Called when the checkout process and cart clearing is entirely successful.
         */
        void onSuccess();

        /**
         * Called when any part of the checkout process fails.
         *
         * @param error A description of the error.
         */
        void onFailure(String error);
    }

    /**
     * Completes a checkout by generating a receipt from the current cart contents,
     * saving it to the user's receipt history, and then clearing the active cart.
     *
     * @param receiptContent A string representation of the receipt details.
     * @param totalSum       The total price of the completed checkout.
     * @param callback       The callback indicating the success or failure of the operation.
     */
    public void checkoutCart(String receiptContent, double totalSum, CheckoutCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onFailure("User not logged in"); return; }

        Map<String, Object> receipt = new HashMap<>();
        receipt.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()).format(new Date()));
        receipt.put("totalPrice", totalSum);
        receipt.put("content", receiptContent);
        receipt.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(uid).collection("receipts").add(receipt)
                .addOnSuccessListener(ref ->
                        db.collection("users").document(uid).collection("cart").get()
                                .addOnSuccessListener(snap -> {
                                    WriteBatch batch = db.batch();
                                    for (QueryDocumentSnapshot doc : snap) batch.delete(doc.getReference());
                                    batch.commit()
                                            .addOnSuccessListener(v -> callback.onSuccess())
                                            .addOnFailureListener(e -> callback.onFailure(
                                                    "Receipt saved, but cart clear failed: " + e.getMessage()));
                                })
                                .addOnFailureListener(e -> callback.onFailure(
                                        "Receipt saved, but cart fetch failed.")))
                .addOnFailureListener(e -> callback.onFailure("Failed to save receipt: " + e.getMessage()));
    }

    /**
     * Represents a historical receipt from a completed checkout.
     */
    public static class ReceiptItem {
        public String date;
        public double totalPrice;
        public String content;
    }

    /**
     * Callback interface for loading receipt history.
     */
    public interface HistoryCallback {
        /**
         * Called when the user's receipt history is successfully loaded.
         *
         * @param historyList The chronological list of receipts.
         */
        void onHistoryLoaded(List<ReceiptItem> historyList);

        /**
         * Called when an error occurs during history retrieval.
         *
         * @param error A description of the error.
         */
        void onError(String error);
    }

    /**
     * Retrieves the authenticated user's chronological receipt history.
     *
     * @param callback The callback returning the list of receipts.
     */
    public void getReceiptHistory(HistoryCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onError("User not logged in"); return; }

        db.collection("users").document(uid).collection("receipts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    List<ReceiptItem> list = new ArrayList<>(snap.size());
                    for (QueryDocumentSnapshot doc : snap) {
                        ReceiptItem item  = new ReceiptItem();
                        item.date         = doc.getString("date");
                        item.totalPrice   = doc.getDouble("totalPrice") != null
                                ? doc.getDouble("totalPrice") : 0.0;
                        item.content      = doc.getString("content");
                        list.add(item);
                    }
                    callback.onHistoryLoaded(list);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}