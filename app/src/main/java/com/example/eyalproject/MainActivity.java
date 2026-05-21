package com.example.eyalproject;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.eyalproject.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

/**
 * The core activity of the application. It handles global navigation using a BottomNavigationView,
 * displays user profile information via a popup window, tracks the user's authentication state,
 * and maintains session continuity using SharedPreferences.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private String username;
    private String email;
    private NavController navController;
    private FirebaseHelper fbHelper;
    private PopupWindow profilePopupWindow;
    private static final String PREFS_NAME = "AppPrefs";

    /**
     * Initializes the main activity, verifying authentication state and setting up
     * the navigation controller for fragments. If the user is unauthenticated, they
     * are instantly logged out.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     * shut down then this Bundle contains the data it most recently
     * supplied.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        username = getIntent().getStringExtra("USERNAME");

        if (username == null) {
            username = prefs.getString("USERNAME", null);
        } else {
            prefs.edit().putString("USERNAME", username).apply();
        }

        fbHelper = new FirebaseHelper();

        if (username == null || fbHelper.getCurrentUserId() == null) {
            logoutUser();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        BottomNavigationView navView = findViewById(R.id.nav_view);

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_store,
                R.id.navigation_service)
                .build();

        navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        if (username != null) {
            TextView usernameTextView = findViewById(R.id.usernameTextView);
            if (usernameTextView != null) {
                usernameTextView.setText("Welcome, " + username + "!");
                usernameTextView.setTextColor(Color.WHITE);
            }

            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            }

            setupProfileIcon();
            setupNavigationWithUsername();
        }

        navView.getMenu().findItem(R.id.navigation_cart).setVisible(true);

        navView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_cart) {
                fbHelper.getCartItemCount(count -> {
                    if (count == 0) {
                        LayoutInflater inflater = getLayoutInflater();
                        View layout = inflater.inflate(R.layout.toast_layout,
                                (ViewGroup) findViewById(R.id.custom_toast_container));

                        TextView text = layout.findViewById(R.id.toast_text);
                        text.setText("Cart is empty");
                        Toast toast = new Toast(getApplicationContext());
                        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        toast.setDuration(Toast.LENGTH_SHORT);
                        toast.setView(layout);
                        toast.show();
                    } else {
                        navController.navigate(R.id.navigation_cart);
                    }
                });
                return false;
            }

            NavOptions navOptions = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(false)
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), false, false)
                    .build();

            navController.navigate(itemId, null, navOptions);
            return true;
        });
        //migrateProductsToFirebase();
    }

    /**
     * Initializes the listener for the user profile image to trigger the profile popup.
     */
    private void setupProfileIcon() {
        ImageView profileIcon = findViewById(R.id.user_profile_image);
        if (profileIcon != null) {
            profileIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showProfileDialog();
                }
            });
        }
    }

    /**
     * Terminates the user's session, clears preferences, signs them out of Firebase,
     * and redirects them to the WelcomeActivity.
     */
    private void logoutUser() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().clear().apply();

        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Sets up a destination listener on the navigation controller to attach user-specific
     * arguments when navigating to particular fragments, such as the service fragment.
     */
    private void setupNavigationWithUsername() {
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination.getId() == R.id.navigation_service) {
                Bundle bundle = new Bundle();
                bundle.putString("USERNAME", username);
            }
        });
    }

    /**
     * Gets the currently authenticated user's username.
     *
     * @return The string representation of the username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Handles Up navigation via the active NavController.
     *
     * @return True if navigation was successful, false otherwise.
     */
    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
    /**
     * Displays a customized profile dropdown using a PopupWindow. Binds user details
     * and provides a logout interaction.
     */
    private void showProfileDialog() {
        if (profilePopupWindow != null && profilePopupWindow.isShowing()) {
            profilePopupWindow.dismiss();
        }

        View dropdownView = getLayoutInflater().inflate(R.layout.profile_dropdown, null);
        TextView tvUsername = dropdownView.findViewById(R.id.tvDropdownUsername);
        TextView tvEmail = dropdownView.findViewById(R.id.tvDropdownEmail);
        Button btnLogout = dropdownView.findViewById(R.id.btnDropdownLogout);

        tvUsername.setText(username);
        tvEmail.setText(email != null ? email : "Not available");

        profilePopupWindow = new PopupWindow(dropdownView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
        );

        profilePopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        profilePopupWindow.setElevation(16f);
        profilePopupWindow.setAnimationStyle(android.R.style.Animation_Dialog);

        ImageView profileIcon = findViewById(R.id.user_profile_image);

        int xOffset = dpToPx(-60);
        int yOffset = dpToPx(4);
        profilePopupWindow.showAsDropDown(profileIcon, xOffset, yOffset);

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                profilePopupWindow.dismiss();
                logoutUser();
            }
        });

        dropdownView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    profilePopupWindow.dismiss();
                    return true;
                }
                return false;
            }
        });
    }
    /**
     * Helper utility to convert density-independent pixels (dp) to absolute pixels (px).
     *
     * @param dp The dimension in dp.
     * @return The dimension converted to absolute pixels based on the display metrics.
     */
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    /**
     * Called before the activity is destroyed. Ensures memory is properly freed and
     * dismisses active popups to prevent window leakage exceptions.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;

        if (profilePopupWindow != null && profilePopupWindow.isShowing()) {
            profilePopupWindow.dismiss();
            profilePopupWindow = null;
        }
    }

    /**
     * An internal utility method that bulk uploads default product data to Firestore.
     * Contains predefined definitions of various electronics and accessories.
     */
    private void migrateProductsToFirebase() {
        fbHelper.uploadSingleProduct("Gaming PC Ryzen 7", 1299.99, "https://m.media-amazon.com/images/I/715ey5-SgiL.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("MacBook Pro 16\" M3", 2499.99, "https://www.istoreil.co.il/media/catalog/product/m/a/macbook_pro_16_in_m3_pro_max_space_black_pdp_image_position-1__wwen_2.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("Dell XPS 13 Laptop", 1199.99, "https://m.media-amazon.com/images/I/710EGJBdIML._AC_SL1500_.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("ASUS ROG Gaming Laptop", 1799.99, "https://m.media-amazon.com/images/I/81XZXFH-RZL._AC_SX466_.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("HP Pavilion Desktop", 899.99, "https://m.media-amazon.com/images/I/81Lp4dVJDdL._AC_SX300_SY300_QL70_FMwebp_.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("Lenovo ThinkPad", 1099.99, "https://m.media-amazon.com/images/I/61IRRQ2gWPL.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("Alienware Aurora R15", 2199.99, "https://i.dell.com/is/image/DellContent/content/dam/ss2/product-images/dell-client-products/desktops/alienware-desktops/alienware-aurora-r15-intel/media-gallery/lunar-light-wh-clear-cryo-tech/desktop-alienware-aurora-r15-white-cryo-clear-panel-gallery-1.psd?fmt=png-alpha&pscan=auto&scl=1&wid=3398&hei=3941&qlt=100,1&resMode=sharp2&size=3398,3941&chrss=full&imwidth=5000", "COMPUTERS");
        fbHelper.uploadSingleProduct("Acer Predator Helios", 1499.99, "https://m.media-amazon.com/images/I/71sS7G5ZpQL._AC_SX300_SY300_QL70_FMwebp_.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("Custom Water Cooled PC", 2999.99, "https://i.ytimg.com/vi/-LDwgCbwcJ0/maxresdefault.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("Mac Mini M2 Pro", 1299.99, "https://mikicom.co.il/Cat_499090_4267.webp", "COMPUTERS");
        fbHelper.uploadSingleProduct("Razer Blade 15", 2299.99, "https://m.media-amazon.com/images/I/71kBeFDgCkL._AC_SX466_.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("MSI Gaming Desktop", 1599.99, "https://storage-asset.msi.com/us/picture/feature/desktop/aegis_r_10th/components.png", "COMPUTERS");
        fbHelper.uploadSingleProduct("Google Pixelbook Go", 999.99, "https://m.media-amazon.com/images/I/61y1WjZMBXL.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("Framework Laptop 13", 1049.99, "https://i.pcmag.com/imagery/reviews/03b2FfXfg7dHwHmcZWgeWjz-1.fit_lim.size_1050x591.v1689714556.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("Intel NUC Mini PC", 699.99, "https://m.media-amazon.com/images/I/71g2bpsrkkL._UF894,1000_QL80_.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("Gaming PC Intel i9", 1899.99, "https://m.media-amazon.com/images/I/71A8cat9MuL.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("MacBook Air 15\" M2", 1299.99, "https://img.zap.co.il/pics/8/6/5/1/92221568d.gif", "COMPUTERS");
        fbHelper.uploadSingleProduct("Dell Alienware Laptop", 1999.99, "https://imageio.forbes.com/b-i-forbesimg/jasonevangelho/files/2013/06/Alienware-14-back-angle1.jpg?height=455&width=590&fit=bounds", "COMPUTERS");
        fbHelper.uploadSingleProduct("ASUS ZenBook Pro", 1699.99, "https://d3m9l0v76dty0.cloudfront.net/system/photos/11958509/original/2393556a581afef24867bdecec2caa92.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("HP Spectre x360", 1399.99, "https://d3m9l0v76dty0.cloudfront.net/system/photos/7261614/large/29d61295ca291e27c6e24464314e94f3.jpg", "COMPUTERS");
        fbHelper.uploadSingleProduct("Lenovo Yoga 9i", 1299.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSKFg7ODX9H048bT7d_FIKaCkDnmmLvaYgkdw&s", "COMPUTERS");
        fbHelper.uploadSingleProduct("Microsoft Surface Laptop", 1199.99, "https://cdn-dynmedia-1.microsoft.com/is/image/microsoftcorp/MSFT-Surface-Laptop-6-Sneak-Curosel-Pivot-3?scl=1", "COMPUTERS");
        fbHelper.uploadSingleProduct("All-in-One Desktop", 1299.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT86XPxDxo6rmFQbyAizWFBPXbif378kLYuQQ&s", "COMPUTERS");

        fbHelper.uploadSingleProduct("PlayStation 5 Pro", 599.99, "https://hotstore.hotmobile.co.il/media/catalog/product/cache/a73c0d5d6c75fbb1966fe13af695aeb7/p/s/ps5_cfi2000_pr_01_cmyk_copy_3.png", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Xbox Series X", 499.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRYFRrbQ9WDJ6hiIreMaoOfVVLfR6gzKlr5bw&s", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Nintendo Switch OLED", 349.99, "https://media.gamestop.com/i/gamestop/11149258/Nintendo-Switch-OLED-Console", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("PlayStation VR2", 549.99, "https://gmedia.playstation.com/is/image/SIEPDC/PSVR2-thumbnail-01-en-22feb22?$facebook$", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Xbox Elite Controller", 179.99, "https://www.dominator.co.il/images/itempics/3227_18062019165512_large.jpg", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Steam Deck 512GB", 649.99, "https://www.dominator.co.il/images/itempics/12366_03072024173002_large.jpg", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Nintendo Switch Lite", 199.99, "https://encrypted-tbn2.gstatic.com/shopping?q=tbn:ANd9GcTy8mm4z3vOKMemAHBbih3UGOM13-7Qg2JcL88uMymzsfeh7RX2jweD-vubGKjdwK3JuQBUpUPZgtXjaNoorUIhfjdU1Wn5IbkMI7C__9_5tHnygA9VZW7CEQ", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("PS5 DualSense Edge", 199.99, "https://img.zap.co.il/pics/5/1/9/5/76005915d.gif", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Gaming Chair Pro", 299.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSpEfoIexWYAA0dgJip7eXjd4gYUPv5Hms6Vw&s", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Console Carrying Case", 49.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSUnttcWqSlelKeiIyWsQq3xfjYaRMqR0O8Ug&s", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Xbox Series S", 299.99, "https://katom.shop/wp-content/uploads/2024/03/0b2854b9-a7e7-47dd-b4f8-a371567854b2.png", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("PlayStation 5 Digital", 449.99, "https://m.media-amazon.com/images/I/31MgKgiwAeL._SX342_SY445_QL70_FMwebp_.jpg", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Nintendo Switch Pro", 399.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTNgSwgqn9zt306XQueH68sOZ3tZMe8mtXw9Q&s", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Gaming Headset Pro", 149.99, "https://www.ocpc.co.il/wp-content/uploads/2021/07/61XjjJovijL._AC_SL1500_.jpg", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Controller Charging Dock", 39.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTWiPsXOb7-hc_Qz6ckt6lFVuHIkEfEOvjqog&s", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Gaming Keyboard Mechanical", 129.99, "https://cdn.mos.cms.futurecdn.net/XMDNCcbVWnrYj3zdapKrGb.jpg", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Gaming Mouse Wireless", 89.99, "https://m.media-amazon.com/images/I/61Mk3YqYHpL._UF894,1000_QL80_.jpg", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Console Skin Protector", 24.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQKWcSF6ITgabtaNQcK1l2teypLiMrssgb7-A&s", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Gaming Monitor Stand", 79.99, "https://m.media-amazon.com/images/I/61x6rzhViDL.jpg", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Console Cooling Fan", 34.99, "https://i5.walmartimages.com/seo/Cooling-Fan-Dust-Proof-for-Xbox-Series-X-Console-with-Colorful-Light-Strip-Dust-Cover-Filter-Low-Noise-Top-Fan_95429b84-e533-4b88-a0ce-02465cc65a1e.09800b96c5f3298a98f8a4179098db74.jpeg?odnHeight=768&odnWidth=768&odnBg=FFFFFF", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Gaming Desk Large", 249.99, "https://m.media-amazon.com/images/I/81JnWF-jqPL.jpg", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Controller Thumb Grips", 14.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS-IFiGkWnMjS2Jba73LuN9JlR7PsG7n_htlQ&s", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Console Vertical Stand", 29.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQmKajugOkZTRY95PQXT6pzPZCxE8J3eYQuIA&s", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Gaming Microphone", 99.99, "https://m.media-amazon.com/images/I/71jfzOmq6dL.jpg", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Console Travel Bag", 59.99, "https://m.media-amazon.com/images/I/8173csq7edL._UY1000_.jpg", "GAMING_CONSOLES");
        fbHelper.uploadSingleProduct("Gaming Console Bundle", 699.99, "https://i5.walmartimages.com/seo/Latest-Xbox-Series-X-Gaming-Console-Bundle-1TB-SSD-Black-Xbox-Console-and-Wireless-Controller-with-HALO-Infinity-and-Mytrix-HDMI-Cable_7bfe8527-c6ae-42b3-a403-30a7655ae0c1.9c3ed2a067f4ac136194010f919fe3c4.jpeg", "GAMING_CONSOLES");

        fbHelper.uploadSingleProduct("Samsung 85\" 8K QLED TV", 3999.99, "https://img.zap.co.il/pics/9/4/2/8/77838249d.gif", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("LG 77\" OLED C3", 2499.99, "https://media.us.lg.com/transform/ecomm-PDPGallery-1100x730/af63d767-92db-4c8a-8200-75c2dfadf1c8/md08003930-DZ-2-jpg?io=transform:fill,width:596", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Sony Bravia 65\" 4K", 1799.99, "https://www.traklin.co.il/images/gallery/15696/x75wl.jpg", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Gaming Monitor 32\" 240Hz", 699.99, "https://www.pc365.co.il/wp-content/uploads/2024/12/32GS95UV-B.webp", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("UltraWide Curved Monitor", 899.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSnsRWvqXYGN_0jtQ-bR7AuTtEXLdKq5SZ4Qw&s", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("4K Projector", 1299.99, "https://d3m9l0v76dty0.cloudfront.net/system/photos/15520467/original/6505e1735e8c43a1d98836b9fc588d23.jpg", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Smart TV 55\" 4K UHD", 799.99, "https://www.galeykor.co.il/images/itempics/6328_090620241754362.jpg", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Portable Monitor 15.6\"", 299.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTJqOBSD2BFfH9jJbG8aL8Ni5rcrTAwlIxG9Q&s", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Dual Monitor Stand", 149.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRN0g9Ea5f4DOYrrXzz1_z58-9qCzDbEKvUZw&s", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("TV Soundbar System", 399.99, "https://m.media-amazon.com/images/I/7113LuUzdBL.jpg", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Samsung 75\" 4K QLED", 1999.99, "https://www.citydeal.co.il/images/itempics/18510-3_24042024155704.jpg", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("LG 65\" NanoCell TV", 1199.99, "https://www.electricshop.co.il/images/itempics/65NANO846QA_141220221137541_large.jpg", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Sony 55\" 4K HDR", 899.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSzGFr-xFc0_zWQWt8blPFCzCAjf5ipqvgUcg&s", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Monitor 27\" 4K IPS", 349.99, "https://m.media-amazon.com/images/I/71GRpZb6+vL.jpg", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Gaming Monitor 49\" Super", 1299.99, "https://www.xgaming.co.il/images/itempics/971_130720251344583366_large.jpg", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("TV Wall Mount Full Motion", 129.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTGvHTigFHKAPkBKAyzWurjxRs9IlUAGZ9niw&s", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Monitor Arm Single", 89.99, "https://www.hexcal.com/cdn/shop/files/Hexcal_Single_Monitor_Arm_Front_View_1000x.jpg?v=1752211670", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("TV Cabinet Stand", 199.99, "https://avfgroup.com/us/wp-content/uploads/sites/3/2022/12/fs900varwb-a_04_large.jpg", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Outdoor TV 55\" Weatherproof", 2499.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR25-DQN3JrwFqi9MeCufG1GawH1SWeiaI9LQ&s", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Touch Screen Monitor", 599.99, "https://m.media-amazon.com/images/I/61HFY+Ji+JL.jpg", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Monitor Calibration Tool", 249.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ8jhNA2qjCs5bxYE0tvRSTcu_uO2B1sZSKRw&s", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("TV LED Backlight Kit", 49.99, "https://m.media-amazon.com/images/I/71B4RM9iriL.jpg", "TVS_AND_DISPLAYS");
        fbHelper.uploadSingleProduct("Monitor Privacy Screen", 79.99, "https://us.targus.com/cdn/shop/products/0027620_4vu-privacy-screen-for-235-widescreen-monitors-169-706848.jpg?v=1625678313", "TVS_AND_DISPLAYS");

        fbHelper.uploadSingleProduct("Call of Duty: Modern Warfare III", 69.99, "https://m.media-amazon.com/images/I/71nWtjjUz-L._AC_UF1000,1000_QL80_.jpg", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("EA Sports FC 24", 59.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTws4C1Pyl0xqmMUql2fbCZC0RFkl8eRPQA2Q&s", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("Spider-Man 2 PS5", 69.99, "https://m.media-amazon.com/images/I/81WUPcfQ9OL._AC_UF1000,1000_QL80_.jpg", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("The Legend of Zelda: Tears", 59.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSCcGP83ZMklsXMvEoYk5k3au7iJlzDz9QMRA&s", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("Grand Theft Auto VI", 79.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR5bff_9PYPv8dgHVkEKbj2RgHFMXijTqxNDA&s", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("FIFA 24 Xbox", 59.99, "https://www.king-games.co.il/files/products/product21489_image1_2023-07-31_14-17-16.webp", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("Cyberpunk 2077 Phantom", 49.99, "https://upload.wikimedia.org/wikipedia/en/d/de/Cyberpunk_2077_Phantom_Liberty_cover_art.jpg", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("God of War Ragnarok", 59.99, "https://upload.wikimedia.org/wikipedia/en/e/ee/God_of_War_Ragnar%C3%B6k_cover.jpg", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("NBA 2K24", 69.99, "https://upload.wikimedia.org/wikipedia/en/thumb/4/48/NBA_2K24_cover_art.jpg/250px-NBA_2K24_cover_art.jpg", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("Elden Ring Shadow", 59.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTB8NuBEuxyQPvHryc549LN9A2OV6H7UO4xJg&s", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("Starfield Premium Edition", 89.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQaIL3BkPrzyBHlUyTuh4a1Bw_TPABLwr9oOg&s", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("Ratchet & Clank: Rift", 69.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ5J0NWCTzkx_yi__zBQjReYu28i7v1JSTipQ&s", "VIDEO_GAMES");
        fbHelper.uploadSingleProduct("Returnal PS5", 69.99, "https://m.media-amazon.com/images/I/71kEwiRf03L._AC_UF1000,1000_QL80_.jpg", "VIDEO_GAMES");

        fbHelper.uploadSingleProduct("Samsung Smart Refrigerator", 1899.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSmxjLim8WDBjt6seA0AXWVK2jzSapLujqqJg&s", "HOME_APPLIANCES");
        fbHelper.uploadSingleProduct("LG Inverter Washing Machine", 899.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTsjZhOhdrakoeECEtMjhiujKrlhSxjlKg_6Q&s", "HOME_APPLIANCES");
        fbHelper.uploadSingleProduct("KitchenAid Stand Mixer", 429.99, "https://m.media-amazon.com/images/I/615kwOY9+3L.jpg", "HOME_APPLIANCES");
        fbHelper.uploadSingleProduct("Dyson V15 Vacuum Cleaner", 749.99, "https://m.media-amazon.com/images/I/517RFNhcMJL.jpg", "HOME_APPLIANCES");
        fbHelper.uploadSingleProduct("Ninja Air Fryer", 199.99, "https://m.media-amazon.com/images/I/71+8uTMDRFL.jpg", "HOME_APPLIANCES");
        fbHelper.uploadSingleProduct("Instant Pot Pro", 149.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQgOraL3ytV5TBJGTy-Htqv5shbXOZaarqcpA&s", "HOME_APPLIANCES");
        fbHelper.uploadSingleProduct("Breville Smart Oven", 349.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRLe6SP8X2XG29J8mw_nLknF2Z0cUAyycj1Hw&s", "HOME_APPLIANCES");
        fbHelper.uploadSingleProduct("Philips Air Purifier", 299.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR9ChpB0jFkfqs_j_kXCKHSMmyu22w3CiHpLQ&s", "HOME_APPLIANCES");
        fbHelper.uploadSingleProduct("Keurig K-Elite Coffee Maker", 189.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTLIPwbwfjNd9RWcJZfq9a2ysR7A9_lx7WhRg&s", "HOME_APPLIANCES");
        fbHelper.uploadSingleProduct("Robot Vacuum S9", 999.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSSy8r2jdtntIMuFZFu6Cl7KMLitgjV_Wy89A&s", "HOME_APPLIANCES");
        fbHelper.uploadSingleProduct("Bosch Dishwasher 800", 1099.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRcCurcc_BuZz5DEKUaJfjsLELV8YZtM5sTtQ&s", "HOME_APPLIANCES");

        fbHelper.uploadSingleProduct("Sony WH-1000XM5 Headphones", 399.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSOux4xPx67B3OnDxjDlNlRkvE8msB70vjl7Q&s", "AUDIO_EQUIPMENT");
        fbHelper.uploadSingleProduct("Apple AirPods Pro 2", 249.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRktbsvMfnEHG6TJVW2uH37hubZQb2SKSjrCw&s", "AUDIO_EQUIPMENT");
        fbHelper.uploadSingleProduct("Bose QuietComfort Ultra", 429.99, "https://beingbetter-bose.co.il/cdn/shop/files/86_1000x.png?v=1749727895", "AUDIO_EQUIPMENT");
        fbHelper.uploadSingleProduct("JBL Flip 6 Bluetooth Speaker", 129.99, "https://pcmaster.co.il/image/cache/catalog/i/fg/bi/d30f20564441ee7524f19456b26bfd5f-1000x1000.jpg", "AUDIO_EQUIPMENT");
        fbHelper.uploadSingleProduct("Sonos Beam Soundbar", 449.99, "https://m.media-amazon.com/images/I/51kIR1gKWYL._AC_UF1000,1000_QL80_.jpg", "AUDIO_EQUIPMENT");
        fbHelper.uploadSingleProduct("Sennheiser HD 660S", 499.99, "https://la-bama.co.il/wp-content/uploads/2024/07/Screenshot_57-2-253x298.jpg", "AUDIO_EQUIPMENT");
        fbHelper.uploadSingleProduct("Marshall Stanmore III Speaker", 399.99, "https://img.zap.co.il/pics/8/4/3/7/53717348d.gif", "AUDIO_EQUIPMENT");
        fbHelper.uploadSingleProduct("Audio-Technica AT-LP120X", 349.99, "https://m.media-amazon.com/images/I/61SAte9QzkL.jpg", "AUDIO_EQUIPMENT");
        fbHelper.uploadSingleProduct("Beats Studio Pro", 349.99, "https://m.media-amazon.com/images/I/61u-OaDSfQL._AC_UF894,1000_QL80_.jpg", "AUDIO_EQUIPMENT");

        fbHelper.uploadSingleProduct("iPhone 15 Pro Max 1TB", 1599.99, "https://img.zap.co.il/pics/0/7/9/6/79366970c.gif", "SMARTPHONES");
        fbHelper.uploadSingleProduct("Samsung Galaxy S24 Ultra", 1299.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQKNkvqurPMB8FabyOSgTelVqy1y8HX1Mui5Q&s", "SMARTPHONES");
        fbHelper.uploadSingleProduct("Google Pixel 8 Pro", 999.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQdyH2Om768gdygNfEci_RqXtR3Z0wx-oDVDA&s", "SMARTPHONES");
        fbHelper.uploadSingleProduct("OnePlus 12", 899.99, "https://gadget-mobile.co.il/wp-content/uploads/2024/02/oneplus-12-600x600.jpg", "SMARTPHONES");
        fbHelper.uploadSingleProduct("Xiaomi 14 Pro", 849.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQEDn_0HnKL5GueEeejSVvM3SwZtvA8pHqXQw&s", "SMARTPHONES");
        fbHelper.uploadSingleProduct("Samsung Galaxy Z Fold5", 1799.99, "https://img.zap.co.il/pics/4/1/4/5/78635414c.gif", "SMARTPHONES");
        fbHelper.uploadSingleProduct("iPhone 14 Plus", 899.99, "https://m.media-amazon.com/images/I/61KXZ+vfu3L.jpg", "SMARTPHONES");
        fbHelper.uploadSingleProduct("Google Pixel 7a", 499.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR61FjwJCam-SxmhUQqmkg56ez-oVVLJFgYLQ&s", "SMARTPHONES");
        fbHelper.uploadSingleProduct("Samsung Galaxy A54", 449.99, "https://m.media-amazon.com/images/I/51orKJJMfTL.jpg", "SMARTPHONES");
        fbHelper.uploadSingleProduct("Nothing Phone (2)", 599.99, "https://www.gadgety.co.il/wp-content/themes/main/thumbs/2023/07/Nothing-Phone-2.jpg", "SMARTPHONES");
        fbHelper.uploadSingleProduct("iPhone SE 2024", 429.99, "https://i.redd.it/iphone-se-4th-gen-2024-v0-x939lzbxtfic1.jpg?width=4000&format=pjpg&auto=webp&s=966f57f15908082e025350bc0314f0b021c1eca6", "SMARTPHONES");
        fbHelper.uploadSingleProduct("Samsung Galaxy Z Flip5", 999.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTBrlcCse_oWXr-PepYJKa8083gPqVqOetJnw&s", "SMARTPHONES");

        fbHelper.uploadSingleProduct("Canon EOS R5 Mirrorless", 3899.99, "https://d3m9l0v76dty0.cloudfront.net/system/photos/5240671/large/8f1981cd0e63635905fd91c6d1f468c1.jpg", "CAMERAS");
        fbHelper.uploadSingleProduct("Sony A7 IV Camera", 2499.99, "https://m.media-amazon.com/images/I/71BaBwNek-L.jpg", "CAMERAS");
        fbHelper.uploadSingleProduct("Nikon Z9 Mirrorless", 5499.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSDsKvz44GiTrdLgm4poJZhxCdq4-ydaoBE2w&s", "CAMERAS");
        fbHelper.uploadSingleProduct("GoPro HERO12 Black", 399.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ-IWL_3tLQkqZq4UYN-TAZzLMawV6KUp0wYg&s", "CAMERAS");
        fbHelper.uploadSingleProduct("DJI Mini 4 Pro Drone", 759.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTCtuIsnCCD1fcln5vLJplbDtwfJ-wMU8dmoA&s", "CAMERAS");
        fbHelper.uploadSingleProduct("Fujifilm X-T5", 1699.99, "https://img.zap.co.il/pics/3/3/9/0/74890933c.gif", "CAMERAS");
        fbHelper.uploadSingleProduct("Canon 70-200mm f2.8 Lens", 2099.99, "https://cdn.media.amplience.net/i/canon/zoom-lens-ef-70-200mm-l-is-ii-usm-fsl-w-cap_7fb75a0151624ddfadc3cec199378c96", "CAMERAS");
        fbHelper.uploadSingleProduct("Sony 24-70mm f2.8 Lens", 2199.99, "https://www.sony.co.il/image/9a3029fb7027dcc88601afba0d8c6bf9?fmt=pjpeg&wid=1200&hei=470&bgcolor=F1F5F9&bgc=F1F5F9", "CAMERAS");
        fbHelper.uploadSingleProduct("Insta360 X3 Camera", 449.99, "https://cellfi.co.il/wp-content/uploads/2025/02/insta360.jpg", "CAMERAS");
        fbHelper.uploadSingleProduct("Canon PowerShot G7 X", 749.99, "https://img.zap.co.il/pics/2/2/4/0/92820422c.gif", "CAMERAS");
        fbHelper.uploadSingleProduct("Sony A7C II Camera", 2199.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcST3abZLTSbHyIO5RCxECoRpw33fxJvHJicgw&s", "CAMERAS");

        fbHelper.uploadSingleProduct("ASUS ROG Rapture WiFi 6", 449.99, "https://www.payngo.co.il/cdn-cgi/image/format=auto,metadata=none,quality=90,width=700,height=700/media/catalog/product/c/b/cbb222d7-c677-42b8-8faa-gfhbr.png", "NETWORKING");
        fbHelper.uploadSingleProduct("TP-Link Archer AXE95", 399.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTj3S444qF684K8_Tqg0F3qAPnFjZaxaHVuBQ&s", "NETWORKING");
        fbHelper.uploadSingleProduct("Netgear Orbi WiFi 6E", 899.99, "https://www.netgear.com/uk/media/RBKE963B-NEW_tcm158-152296.webp", "NETWORKING");
        fbHelper.uploadSingleProduct("Google Nest Wifi Pro", 399.99, "https://i.pcmag.com/imagery/reviews/04xrWbRQrmZhC7GG0tgLVlL-7.fit_lim.size_1050x.jpg", "NETWORKING");
        fbHelper.uploadSingleProduct("Ubiquiti Dream Machine", 379.99, "https://gfx3.senetic.com/akeneo-catalog/9/b/2/3/9b23d554efd43a1beac2fa7dce01118f6f0f4a92_1033520_UDM_image4.jpg", "NETWORKING");
        fbHelper.uploadSingleProduct("NETGEAR Nighthawk M6 Pro", 899.99, "https://ksp.co.il/shop/items/512/409979.jpg", "NETWORKING");
        fbHelper.uploadSingleProduct("TP-Link Deco XE75", 299.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQlODZdQZMHOHoifHAyPuk_RuqLfJKkgrPraA&s", "NETWORKING");
        fbHelper.uploadSingleProduct("ASUS ZenWiFi Pro ET12", 799.99, "https://eilat.payngo.co.il/cdn-cgi/image/format=auto,metadata=none,quality=90,width=700,height=700/media/catalog/product/t/g/tguyfhygfdr.png", "NETWORKING");
        fbHelper.uploadSingleProduct("Network Switch 8-Port", 89.99, "https://comservice.co.il/up/gallery/70060_TL-SG108(UN)30_01_normal_15166172.jpg", "NETWORKING");
        fbHelper.uploadSingleProduct("WiFi Extender AC1200", 79.99, "https://www.mi-il.co.il/images/site/products/a2597fbd-a8fd-4937-945e-25675913ff05.jpg", "NETWORKING");
        fbHelper.uploadSingleProduct("Ethernet Cable Cat8", 29.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSt6jHKVyuvebYyiWgETWnKhlSOfE4hj_qLdA&s", "NETWORKING");
        fbHelper.uploadSingleProduct("Network Patch Panel", 149.99, "https://9233480.fs1.hubspotusercontent-na1.net/hubfs/9233480/tailwind-feat-whatisapatchpanel.jpg", "NETWORKING");
        fbHelper.uploadSingleProduct("Powerline Adapter Kit", 99.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRhEUFdkNwbx_BuK97vACjHXE9Kqr4t6LWNDw&s", "NETWORKING");
        fbHelper.uploadSingleProduct("Network Cabinet Wall", 199.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTG9IpyyJy6NPDRuHMORYWEZPbkvcZ2Og7vog&s", "NETWORKING");
        fbHelper.uploadSingleProduct("PoE Injector 48V", 39.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTpHbmXsKcsB79Dn9yQvWGbdstQ_T7NTjvCAw&s", "NETWORKING");
        fbHelper.uploadSingleProduct("Network Cable Tester", 49.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTkQiwwVXd4iE_C2kZQctX4GzFLQxaKNVPdFw&s", "NETWORKING");
        fbHelper.uploadSingleProduct("Wireless Access Point", 129.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT_0bWIvNkvL64AN8syVoZ838RNEovpfk0Ymg&s", "NETWORKING");
        fbHelper.uploadSingleProduct("Network Attached Storage", 599.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR4xrQibQu590kPiy9bqdXaWt8wa6a1e53clA&s", "NETWORKING");

        fbHelper.uploadSingleProduct("Amazon Echo Show 15", 249.99, "https://m.media-amazon.com/images/I/61xQl81iYQL._UF1000,1000_QL80_.jpg", "SMART_HOME");
        fbHelper.uploadSingleProduct("Google Nest Hub Max", 229.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRL2hc4dlUPbIlkwWNvy8ibBTvvRTg9XVW7vw&s", "SMART_HOME");
        fbHelper.uploadSingleProduct("Philips Hue Starter Kit", 199.99, "https://www.assets.signify.com/is/image/Signify/046677563080-929002469109-Philips-Hue_W-10_5W-A19-E26-2set-US-RTP", "SMART_HOME");
        fbHelper.uploadSingleProduct("Ring Video Doorbell Pro 2", 249.99, "https://images.ctfassets.net/a3peezndovsu/variant-31961428492377/e8d3f08c98ee484eef46c383b85cb785/variant-31961428492377.jpg", "SMART_HOME");
        fbHelper.uploadSingleProduct("Nest Learning Thermostat", 249.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQVNC7RaDNQ6JSPYj8kLargzhWInRGhlGjBIw&s", "SMART_HOME");
        fbHelper.uploadSingleProduct("August Smart Lock Pro", 279.99, "https://m.media-amazon.com/images/I/519AkRwE2pL.jpg", "SMART_HOME");
        fbHelper.uploadSingleProduct("Arlo Pro 4 Security Camera", 199.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSRvg2BLNJdCfLYbeInGAx97zBny9K3SXXfAg&s", "SMART_HOME");
        fbHelper.uploadSingleProduct("Samsung SmartThings Hub", 129.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTl152bc6-WkwKUuj5WaZhN5DQq8-RSHaOksQ&s", "SMART_HOME");
        fbHelper.uploadSingleProduct("Wyze Cam Pan v3", 49.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS07mM2cvm50q45yU9hjRg_faDBdeRPcpVapQ&s", "SMART_HOME");
        fbHelper.uploadSingleProduct("Smart Plug 4-Pack", 39.99, "https://m.media-amazon.com/images/I/51zoLDBO0wL.jpg", "SMART_HOME");
        fbHelper.uploadSingleProduct("Smart Light Bulb RGB", 24.99, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcStvjmQbF2CsJD6xeMaJy5s81EOF25CgjHPNg&s", "SMART_HOME");

        Toast.makeText(this, "All Products Uploaded to Firebase! No duplicates created.", Toast.LENGTH_LONG).show();
    }
}