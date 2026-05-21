package com.example.eyalproject.ui.cart;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton; // Added for the trash icon
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.eyalproject.FirebaseHelper;
import com.example.eyalproject.MainActivity;
import com.example.eyalproject.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A fragment that manages the user's shopping cart. It handles displaying the cart items,
 * calculating total sums, processing mock payments, managing purchase history, and
 * generating digital receipts. Interacts directly with Firebase to persist and retrieve data.
 */
public class CartFragment extends Fragment {

    private FirebaseHelper fbHelper;
    private TableLayout tableLayout;
    private TextView textViewTotalSum;
    private View btnBuyAll;
    private Button btnViewHistory;

    private PopupWindow receiptPopup;
    private AlertDialog paymentDialog;
    private PopupWindow historyPopup;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private double currentCartTotal = 0.0;

    /**
     * Called to have the fragment instantiate its user interface view.
     * Initializes Firebase integration and UI references.
     *
     * @param inflater           The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container          If non-null, this is the parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state.
     * @return Return the View for the fragment's UI.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_cart, container, false);

        fbHelper = new FirebaseHelper();

        tableLayout = root.findViewById(R.id.tableLayout);
        textViewTotalSum = root.findViewById(R.id.textViewTotalSum);
        btnBuyAll = root.findViewById(R.id.btnBuyAll);
        btnViewHistory = root.findViewById(R.id.btnViewHistory);

        return root;
    }

    /**
     * Called when the fragment is visible to the user and actively running.
     * Triggers the cart data load and sets up the primary action listeners for checkout and history.
     */
    @Override
    public void onResume() {
        super.onResume();
        loadCartDataAsync();

        btnBuyAll.setOnClickListener(v -> {
            if (fbHelper.getCurrentUserId() == null) {
                showCustomToast("User not logged in", R.drawable.ic_store, R.color.error);
                return;
            }

            if (currentCartTotal > 0.0) {
                showPaymentPopup(currentCartTotal);
            } else {
                showCustomToast("Your cart is empty", R.drawable.ic_store, R.color.info_color);
            }
        });

        btnViewHistory.setOnClickListener(v -> showHistoryPopup());
    }

    /**
     * Fetches the current user's cart items asynchronously from Firebase Firestore.
     * Upon success, updates the local cart total and triggers the UI refresh.
     */
    private void loadCartDataAsync() {
        String userId = fbHelper.getCurrentUserId();
        if (userId == null) {
            showEmptyCartState();
            return;
        }

        fbHelper.getCartItems(new FirebaseHelper.CartCallback() {
            @Override
            public void onCartLoaded(List<FirebaseHelper.CartItem> items, double totalSum) {
                if (!isAdded() || getContext() == null) return;

                currentCartTotal = totalSum;
                displayOrdersUI(items);
                updateTotalSumUI(totalSum);
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getContext() == null) return;
                showCustomToast("Error loading cart: " + error, R.drawable.ic_store, R.color.error);
            }
        });
    }

    /**
     * Clears the current table layout and populates it with rows representing the fetched cart items.
     * Displays an empty state if the item list is empty.
     *
     * @param items The list of cart items to display.
     */
    private void displayOrdersUI(List<FirebaseHelper.CartItem> items) {
        tableLayout.removeAllViews();
        if (items.isEmpty()) {
            showEmptyCartState();
        } else {
            for (FirebaseHelper.CartItem item : items) {
                TableRow tableRow = createOrderRow(item);
                tableLayout.addView(tableRow);
            }
        }
    }

    /**
     * Updates the UI element displaying the total calculated sum of the cart.
     * Enables or disables the checkout button based on whether the total is greater than zero.
     *
     * @param totalSum The calculated total price.
     */
    private void updateTotalSumUI(double totalSum) {
        textViewTotalSum.setText(String.format(Locale.getDefault(), "$%.2f", totalSum));
        if (btnBuyAll != null) {
            boolean hasItems = totalSum > 0;
            btnBuyAll.setEnabled(hasItems);
            btnBuyAll.setAlpha(hasItems ? 1f : 0.5f);
        }
    }

    /**
     * Inflates and displays a payment confirmation dialog requiring the user to input
     * standard credit card details to proceed with the checkout.
     *
     * @param totalAmount The total amount to be charged, displayed on the confirmation button.
     */
    private void showPaymentPopup(double totalAmount) {
        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.payment_popup, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(popupView);

        final EditText editTextCardNumber = popupView.findViewById(R.id.editTextCardNumber);
        final EditText editTextExpiryDate = popupView.findViewById(R.id.editTextExpiryDate);
        final EditText editTextCVV = popupView.findViewById(R.id.editTextCVV);
        final TextView textViewPaymentError = popupView.findViewById(R.id.textViewPaymentError);
        final Button btnPayNow = popupView.findViewById(R.id.btnPayNow);
        final Button btnCancelPayment = popupView.findViewById(R.id.btnCancelPayment);

        btnPayNow.setText(String.format(Locale.getDefault(), "Pay $%.2f", totalAmount));
        addExpiryDateTextWatcher(editTextExpiryDate);
        paymentDialog = builder.create();

        btnPayNow.setOnClickListener(v -> {
            String cardNumber = editTextCardNumber.getText().toString().replaceAll("\\s", "");
            String expiryDate = editTextExpiryDate.getText().toString();
            String cvv = editTextCVV.getText().toString();

            if (validatePaymentDetails(cardNumber, expiryDate, cvv, textViewPaymentError)) {
                showCustomToast("Payment Processing...", R.drawable.ic_store, R.color.primary_color);
                buyAll();
                paymentDialog.dismiss();
            }
        });

        btnCancelPayment.setOnClickListener(v -> paymentDialog.dismiss());
        paymentDialog.setCanceledOnTouchOutside(false);
        paymentDialog.show();
    }

    /**
     * Validates the format and logic of the provided payment details.
     *
     * @param cardNumber    The input credit card number.
     * @param expiryDate    The input expiration date.
     * @param cvv           The input CVV.
     * @param errorTextView The TextView used to display specific validation errors.
     * @return True if all details are valid, False otherwise.
     */
    private boolean validatePaymentDetails(String cardNumber, String expiryDate, String cvv, TextView errorTextView) {
        if (TextUtils.isEmpty(cardNumber) || TextUtils.isEmpty(expiryDate) || TextUtils.isEmpty(cvv)) {
            errorTextView.setText("All fields are required.");
            errorTextView.setVisibility(View.VISIBLE);
            return false;
        }
        if (cardNumber.length() != 16 || !TextUtils.isDigitsOnly(cardNumber)) {
            errorTextView.setText("Invalid Card Number (must be 16 digits).");
            errorTextView.setVisibility(View.VISIBLE);
            return false;
        }
        if ((cvv.length() < 3 || cvv.length() > 4) || !TextUtils.isDigitsOnly(cvv)) {
            errorTextView.setText("Invalid CVV (must be 3 or 4 digits).");
            errorTextView.setVisibility(View.VISIBLE);
            return false;
        }
        if (!expiryDate.matches("^(0[1-9]|1[0-2])/([0-9]{2})$")) {
            errorTextView.setText("Invalid Expiry Date format (MM/YY).");
            errorTextView.setVisibility(View.VISIBLE);
            return false;
        }
        try {
            String[] parts = expiryDate.split("/");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]) + 2000;
            Calendar currentCal = Calendar.getInstance();
            int currentMonth = currentCal.get(Calendar.MONTH) + 1;
            int currentYear = currentCal.get(Calendar.YEAR);
            if (year < currentYear || (year == currentYear && month < currentMonth)) {
                errorTextView.setText("Card is expired.");
                errorTextView.setVisibility(View.VISIBLE);
                return false;
            }
        } catch (Exception e) {
            errorTextView.setText("Error parsing expiry date.");
            errorTextView.setVisibility(View.VISIBLE);
            return false;
        }
        errorTextView.setVisibility(View.GONE);
        return true;
    }

    /**
     * Adds a TextWatcher to automatically format the expiration date input with a slash (MM/YY).
     *
     * @param et The EditText to which the formatter is applied.
     */
    private void addExpiryDateTextWatcher(EditText et) {
        et.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                if (text.equals(current)) {
                    return;
                }
                String digits = text.replaceAll("\\D", "");
                int len = digits.length();
                String formatted;
                if (len <= 2) {
                    formatted = digits;
                } else {
                    formatted = digits.substring(0, 2) + "/" + digits.substring(2);
                }
                if (formatted.length() > 5) {
                    formatted = formatted.substring(0, 5);
                }
                current = formatted;
                et.setText(formatted);
                et.setSelection(formatted.length());
            }
        });
    }

    /**
     * Finalizes the checkout process by generating a digital receipt from the current cart items,
     * saving it to the user's history in Firebase, and clearing the active cart.
     */
    private void buyAll() {
        if (fbHelper.getCurrentUserId() == null) {
            showCustomToast("User session expired. Please log in.", R.drawable.ic_store, R.color.error);
            return;
        }

        fbHelper.getCartItems(new FirebaseHelper.CartCallback() {
            @Override
            public void onCartLoaded(List<FirebaseHelper.CartItem> items, double totalSum) {
                if (!isAdded() || getContext() == null) return;

                String receipt = generateDigitalReceipt(items, totalSum);

                fbHelper.checkoutCart(receipt, totalSum, new FirebaseHelper.CheckoutCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded() || getContext() == null) return;

                        showReceiptPopup(receipt);
                        schedulePurchaseNotification();
                        loadCartDataAsync();
                        showCustomToast("Purchase successful! History saved.", R.drawable.ic_store, R.color.success_color);
                    }

                    @Override
                    public void onFailure(String error) {
                        if (!isAdded() || getContext() == null) return;

                        saveReceiptToFile(receipt);
                        showCustomToast("Payment successful but failed to save history to DB. Saved to file.",
                                R.drawable.ic_store, R.color.error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                showCustomToast("Checkout failed: Could not retrieve cart data.", R.drawable.ic_store, R.color.error);
            }
        });
    }

    /**
     * Displays a placeholder empty state view within the table layout when no items exist in the cart.
     */
    private void showEmptyCartState() {
        tableLayout.removeAllViews();
        TableRow emptyRow = new TableRow(getContext());
        TableLayout.LayoutParams params = new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 100, 0, 0);
        emptyRow.setLayoutParams(params);

        TextView emptyText = new TextView(getContext());
        emptyText.setText("🛒\nYour Cart is Empty");
        emptyText.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setTextSize(18);
        emptyText.setLineSpacing(1.2f, 1.2f);
        emptyText.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        emptyRow.addView(emptyText);
        tableLayout.addView(emptyRow);
    }

    /**
     * Constructs a TableRow designed to represent a single cart item, including its name,
     * total price based on quantity, and an interactive removal button.
     *
     * @param item The CartItem instance defining the row's data.
     * @return The fully constructed TableRow.
     */
    private TableRow createOrderRow(FirebaseHelper.CartItem item) {
        TableRow tableRow = new TableRow(getContext());
        tableRow.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.rounded_card));
        tableRow.setGravity(Gravity.CENTER_VERTICAL); // Align the icon, price, and text vertically

        TableLayout.LayoutParams layoutParams = new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, 0, 0, 8);
        tableRow.setLayoutParams(layoutParams);

        // 1. Order Name
        TextView textViewOrderName = new TextView(getContext());
        textViewOrderName.setText(item.productName + " (x" + item.quantity + ")");
        textViewOrderName.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        textViewOrderName.setTextSize(16);
        textViewOrderName.setPadding(16, 20, 8, 20);
        textViewOrderName.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f));

        // 2. Order Price
        TextView textViewOrderPrice = new TextView(getContext());
        textViewOrderPrice.setText(String.format(Locale.getDefault(), "$%.2f", item.price * item.quantity));
        textViewOrderPrice.setTextColor(ContextCompat.getColor(getContext(), R.color.primary_color));
        textViewOrderPrice.setTextSize(16);
        textViewOrderPrice.setTypeface(null, Typeface.BOLD);
        textViewOrderPrice.setPadding(8, 20, 8, 20);
        textViewOrderPrice.setGravity(Gravity.CENTER);
        textViewOrderPrice.setSingleLine(true);
        textViewOrderPrice.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.2f));

        // 3. Remove Item Icon Button (Trash Can)
        ImageButton btnRemoveItem = new ImageButton(getContext());
        btnRemoveItem.setImageResource(android.R.drawable.ic_menu_delete); // Built-in Android trash icon
        btnRemoveItem.setBackgroundColor(Color.TRANSPARENT); // Remove the boxy background
        btnRemoveItem.setColorFilter(ContextCompat.getColor(getContext(), R.color.error)); // Tint to red
        btnRemoveItem.setPadding(8, 20, 16, 20);

        // Use a smaller layout weight (0.8f) so it takes up less space than the text
        btnRemoveItem.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.8f));

        btnRemoveItem.setOnClickListener(v -> {
            fbHelper.deleteCartItem(item.documentId, new FirebaseHelper.ActionCallback() {
                @Override
                public void onSuccess() {
                    if (!isAdded() || getContext() == null) return;
                    showCustomToast("Item removed from cart", R.drawable.ic_store, R.color.success_color);
                    loadCartDataAsync();
                }

                @Override
                public void onFailure(String error) {
                    if (!isAdded() || getContext() == null) return;
                    showCustomToast("Failed to remove item", R.drawable.ic_store, R.color.error);
                }
            });
        });

        tableRow.addView(textViewOrderName);
        tableRow.addView(textViewOrderPrice);
        tableRow.addView(btnRemoveItem);

        return tableRow;
    }

    /**
     * Presents a popup window displaying the user's chronological purchase history.
     * Fetches the historical receipt data asynchronously from Firebase.
     */
    private void showHistoryPopup() {
        if (getContext() == null || getView() == null) return;

        if (fbHelper.getCurrentUserId() == null) {
            showCustomToast("User session required to view history.", R.drawable.ic_store, R.color.error);
            return;
        }

        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.history_popup, null);
        LinearLayout historyContainer = popupView.findViewById(R.id.historyPopupContainer);
        Button btnCloseHistory = popupView.findViewById(R.id.btnCloseHistory);

        TextView loadingText = new TextView(getContext());
        loadingText.setText("Loading history...");
        loadingText.setGravity(Gravity.CENTER);
        historyContainer.addView(loadingText);

        historyPopup = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true
        );
        historyPopup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        historyPopup.setElevation(20f);
        historyPopup.showAtLocation(requireView(), Gravity.CENTER, 0, 0);

        fbHelper.getReceiptHistory(new FirebaseHelper.HistoryCallback() {
            @Override
            public void onHistoryLoaded(List<FirebaseHelper.ReceiptItem> historyList) {
                if (!isAdded() || getContext() == null) return;
                historyContainer.removeAllViews();

                if (historyList.isEmpty()) {
                    TextView emptyText = new TextView(getContext());
                    emptyText.setText("No purchase history yet. Buy something!");
                    emptyText.setGravity(Gravity.CENTER);
                    emptyText.setPadding(0, 50, 0, 50);
                    emptyText.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
                    historyContainer.addView(emptyText);
                } else {
                    TableLayout historyTable = new TableLayout(getContext());
                    historyTable.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    historyTable.setStretchAllColumns(true);

                    historyTable.addView(createHistoryHeader());
                    for (FirebaseHelper.ReceiptItem item : historyList) {
                        historyTable.addView(createHistoryRow(item));
                    }
                    historyContainer.addView(historyTable);
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getContext() == null) return;
                historyContainer.removeAllViews();
                TextView errText = new TextView(getContext());
                errText.setText("Failed to load history.");
                historyContainer.addView(errText);
            }
        });

        btnCloseHistory.setOnClickListener(v -> historyPopup.dismiss());
    }

    /**
     * Generates a styled header row for the purchase history table.
     *
     * @return The constructed header TableLayout.
     */
    private TableLayout createHistoryHeader() {
        TableLayout headerLayout = new TableLayout(getContext());
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        headerLayout.setStretchAllColumns(true);

        TableRow headerRow = new TableRow(getContext());
        headerRow.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.light_gray));
        headerRow.setPadding(16, 16, 16, 16);

        String[] headers = {"Date", "Price", "Action"};
        int[] weights = {2, 2, 2};
        for (int i = 0; i < headers.length; i++) {
            TextView tv = new TextView(getContext());
            tv.setText(headers[i]);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
            TableRow.LayoutParams params = new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, weights[i]);
            tv.setLayoutParams(params);
            headerRow.addView(tv);
        }
        headerLayout.addView(headerRow);
        return headerLayout;
    }

    /**
     * A helper method to create uniformly styled TextView instances for table cells.
     *
     * @param context The application context.
     * @param text    The text string to display.
     * @param weight  The layout weight defining the cell's horizontal span.
     * @param color   The resource color value for the text.
     * @param isBold  Boolean dictating if the text should be bolded.
     * @return The constructed TextView.
     */
    private TextView createTextView(Context context, String text, float weight, int color, boolean isBold) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (isBold) tv.setTypeface(null, Typeface.BOLD);

        TableRow.LayoutParams params = new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(params);
        return tv;
    }

    /**
     * Constructs a TableRow that visually represents a single historical purchase,
     * complete with an action button to view the full digital receipt.
     *
     * @param item The ReceiptItem instance defining the historical transaction.
     * @return The configured TableRow.
     */
    private TableRow createHistoryRow(FirebaseHelper.ReceiptItem item) {
        Context context = getContext();
        if (context == null) return new TableRow(requireContext());

        TableRow row = new TableRow(context);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(16, 10, 16, 10);

        int textColorSecondary = ContextCompat.getColor(context, R.color.text_secondary);
        int primaryColor = ContextCompat.getColor(context, R.color.primary_color);

        String shortDate = item.date.length() >= 10 ? item.date.substring(0, 10) : item.date;
        TextView tvDate = createTextView(context, shortDate, 2f, textColorSecondary, false);
        row.addView(tvDate);

        TextView tvPrice = createTextView(context, String.format(Locale.getDefault(), "$%.2f", item.totalPrice), 2f, primaryColor, true);
        row.addView(tvPrice);

        Button btnViewReceipt = new Button(context);
        btnViewReceipt.setText("View");
        btnViewReceipt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnViewReceipt.setBackgroundColor(primaryColor);
        btnViewReceipt.setTextColor(Color.WHITE);
        btnViewReceipt.setPadding(8, 0, 8, 0);

        btnViewReceipt.setOnClickListener(v -> showReceiptPopup(item.content));

        TableRow.LayoutParams buttonParams = new TableRow.LayoutParams(0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, getResources().getDisplayMetrics()), 2f);
        btnViewReceipt.setLayoutParams(buttonParams);
        row.addView(btnViewReceipt);

        return row;
    }

    /**
     * Uses the AlarmManager to schedule a simulated local notification confirming
     * that the purchase is being processed.
     */
    private void schedulePurchaseNotification() {
        try {
            AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(requireContext(), CartReminderReceiver.class);
            intent.setAction("PURCHASE_CONFIRMATION");
            intent.putExtra("message", "Thank you for your purchase! Your order is being processed.");
            intent.putExtra("title", "Purchase Confirmation");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    requireContext(),
                    (int) System.currentTimeMillis(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            long triggerTime = System.currentTimeMillis() + 5000;

            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } catch (Exception e) {
            e.printStackTrace();
            showCustomToast("Purchase successful but failed to schedule notification", R.drawable.ic_store, R.color.info_color);
        }
    }

    /**
     * Initializes and displays a popup over the current screen containing the full text
     * of a digital receipt, and provides a mechanism to share it.
     *
     * @param receiptContent The formatted text contents of the receipt.
     */
    private void showReceiptPopup(String receiptContent) {
        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.receipt_popup, null);
        TextView textViewReceipt = popupView.findViewById(R.id.textViewReceipt);
        textViewReceipt.setText(receiptContent);

        receiptPopup = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true
        );
        receiptPopup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        receiptPopup.setElevation(20f);

        View rootView = getView();
        if (rootView != null) {
            receiptPopup.showAtLocation(rootView, Gravity.CENTER, 0, 0);
        }

        Button btnClose = popupView.findViewById(R.id.btnCloseReceipt);
        btnClose.setOnClickListener(v -> {
            if (receiptPopup != null && receiptPopup.isShowing()) {
                receiptPopup.dismiss();
            }
        });

        Button btnShare = popupView.findViewById(R.id.btnShareReceipt);
        btnShare.setOnClickListener(v -> shareReceipt(receiptContent));

        receiptPopup.setOutsideTouchable(true);
        receiptPopup.setFocusable(true);
    }

    /**
     * Triggers a system share sheet allowing the user to send the text content
     * of the digital receipt to other applications.
     *
     * @param receiptContent The formatted receipt string to be shared.
     */
    private void shareReceipt(String receiptContent) {
        try {
            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Purchase Receipt");
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, receiptContent);
            startActivity(android.content.Intent.createChooser(shareIntent, "Share receipt via"));
        } catch (Exception e) {
            showCustomToast("No sharing app available", R.drawable.ic_store, R.color.info_color);
        }
    }

    /**
     * Formats the list of purchased cart items and the total sum into a clean,
     * human-readable digital receipt string.
     *
     * @param items    The list of items purchased.
     * @param totalSum The overall sum of the purchase.
     * @return A formatted text string acting as the receipt.
     */
    private String generateDigitalReceipt(List<FirebaseHelper.CartItem> items, double totalSum) {
        StringBuilder receipt = new StringBuilder();
        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        receipt.append("🛍️ DIGITAL RECEIPT 🛍️\n");
        receipt.append("======================\n");
        receipt.append("Date: ").append(currentDate).append("\n");
        receipt.append("Order ID: ORD").append(System.currentTimeMillis()).append("\n");
        receipt.append("Status: PAID ✅\n\n");

        receipt.append("ITEMS PURCHASED:\n");
        receipt.append("----------------\n");

        double subtotal = 0;
        for (FirebaseHelper.CartItem item : items) {
            double itemTotal = item.price * item.quantity;
            subtotal += itemTotal;
            receipt.append(String.format(Locale.getDefault(), "• %-20s x%d $%.2f\n", item.productName, item.quantity, itemTotal));
        }

        receipt.append("----------------\n");
        receipt.append(String.format(Locale.getDefault(), "SUBTOTAL:       $%.2f\n", subtotal));
        receipt.append(String.format(Locale.getDefault(), "TOTAL:          $%.2f\n", totalSum));
        receipt.append("======================\n");
        receipt.append("Thank you for your purchase! 🎉\n");
        receipt.append("We hope to see you again soon!\n\n");
        receipt.append("Generated by Eyal Project App");

        return receipt.toString();
    }

    /**
     * Resolves and creates the local directory structure intended for saving fallback receipts.
     *
     * @return The directory File object.
     */
    private File getReceiptsDirectory() {
        File receiptsDir = new File(requireContext().getFilesDir(), "Receipts");
        if (!receiptsDir.exists()) {
            receiptsDir.mkdirs();
        }
        return receiptsDir;
    }

    /**
     * Provides a fallback mechanism to save a generated digital receipt to local device storage
     * in the event that saving to the cloud database fails.
     *
     * @param receipt The text content of the receipt.
     * @return True if the file write operation was successful, False otherwise.
     */
    private boolean saveReceiptToFile(String receipt) {
        FileOutputStream fos = null;
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "receipt_" + timeStamp + ".txt";

            File receiptsDir = getReceiptsDirectory();
            File receiptFile = new File(receiptsDir, fileName);

            fos = new FileOutputStream(receiptFile);
            fos.write(receipt.getBytes());
            fos.flush();
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Inflates a custom layout to display an engaging, styled toast notification.
     * Falls back to a standard Android Toast if custom inflation fails.
     *
     * @param message          The message string to be displayed.
     * @param iconResId        The drawable resource ID for the icon.
     * @param typeColorResId   The color resource ID determining the toast's visual theme.
     */
    private void showCustomToast(String message, int iconResId, int typeColorResId) {
        if (getContext() == null || getActivity() == null) return;

        try {
            LayoutInflater inflater = getLayoutInflater();
            View layout = inflater.inflate(R.layout.custom_toast,
                    (ViewGroup) getActivity().findViewById(R.id.custom_toast_container));

            TextView text = layout.findViewById(R.id.toast_text);
            ImageView icon = layout.findViewById(R.id.toast_icon);
            LinearLayout container = layout.findViewById(R.id.custom_toast_container);

            int color = ContextCompat.getColor(getContext(), typeColorResId);
            container.getBackground().mutate().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);

            text.setText(message);
            icon.setImageResource(iconResId);


            Toast toast = new Toast(getContext());
            toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 150);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setView(layout);
            toast.show();
        } catch (Exception e) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    /**
     * Called when the view previously created by onCreateView has been detached from the fragment.
     * Explicitly dismisses any open dialogs or popups to prevent window leak exceptions.
     */
    @Override
    public void onDestroyView() {
        if (receiptPopup != null && receiptPopup.isShowing()) {
            receiptPopup.dismiss();
        }
        if (paymentDialog != null && paymentDialog.isShowing()) {
            paymentDialog.dismiss();
        }
        if (historyPopup != null && historyPopup.isShowing()) {
            historyPopup.dismiss();
        }
        super.onDestroyView();
    }
}