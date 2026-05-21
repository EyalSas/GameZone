package com.example.eyalproject.ui.about;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eyalproject.Models.Place;
import com.example.eyalproject.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fragment responsible for displaying company store locations. It offers a dual-view interface,
 * allowing users to seamlessly toggle between a scrolling list representation using a RecyclerView
 * and a geographical representation using an embedded Google Map.
 */
public class AboutFragment extends Fragment implements OnMapReadyCallback {

    private MapView mapView;
    private GoogleMap googleMap;
    private RecyclerView storesRecyclerView;
    private FloatingActionButton toggleFab;
    private List<Place> gameStores;
    private PlaceAdapter placeAdapter;
    private boolean showingMap = false;
    private Map<String, Marker> markerMap = new HashMap<>();

    /**
     * Called to have the fragment instantiate its user interface view.
     * Initializes the view bindings, populates the store data, sets up the RecyclerView,
     * and asynchronously loads the Google Map instance.
     *
     * @param inflater           The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container          If non-null, this is the parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state.
     * @return Return the View for the fragment's UI.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_about, container, false);

        initializeViews(rootView);
        initializeGameStores();
        setupRecyclerView();

        toggleFab.setOnClickListener(v -> toggleView());
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        }

        return rootView;
    }

    /**
     * Binds local variables to their corresponding UI elements within the inflated layout hierarchy.
     *
     * @param rootView The root view of the fragment layout.
     */
    private void initializeViews(View rootView) {
        mapView = rootView.findViewById(R.id.mapView);
        storesRecyclerView = rootView.findViewById(R.id.storesRecyclerView);
        toggleFab = rootView.findViewById(R.id.toggleFab);
    }

    /**
     * Populates the internal data structure with predefined physical store locations,
     * including geographical coordinates and contact information.
     */
    private void initializeGameStores() {
        gameStores = new ArrayList<>();
        gameStores.add(new Place(1L, "GameZone Jerusalem", 31.7683, 35.2137, "+972-2-500-1001", "Jaffa St 123, Jerusalem"));
        gameStores.add(new Place(2L, "GameZone Tel Aviv", 32.0853, 34.7818, "+972-3-500-1002", "Dizengoff St 45, Tel Aviv"));
        gameStores.add(new Place(3L, "GameZone Haifa", 32.7940, 34.9896, "+972-4-500-1003", "HaAtzmaut St 67, Haifa"));
        gameStores.add(new Place(4L, "GameZone Be'er Sheva", 31.2529, 34.7915, "+972-8-500-1004", "Rager Blvd 89, Be'er Sheva"));
        gameStores.add(new Place(5L, "GameZone Netanya", 32.3320, 34.8590, "+972-9-500-1005", "Herzl St 34, Netanya"));
        gameStores.add(new Place(6L, "GameZone Ashdod", 31.8044, 34.6553, "+972-8-500-1006", "HaShalom St 56, Ashdod"));
        gameStores.add(new Place(7L, "GameZone Rishon LeZion", 31.9730, 34.7925, "+972-3-500-1007", "Moshe Levi St 78, Rishon LeZion"));
        gameStores.add(new Place(8L, "GameZone Petah Tikva", 32.0871, 34.8875, "+972-3-500-1008", "Jabotinsky St 90, Petah Tikva"));
        gameStores.add(new Place(9L, "GameZone Holon", 32.0158, 34.7874, "+972-3-500-1009", "Golda Meir St 12, Holon"));
        gameStores.add(new Place(10L, "GameZone Eilat", 29.5577, 34.9519, "+972-8-500-1010", "HaTmarim Blvd 34, Eilat"));
    }

    /**
     * Configures the RecyclerView by assigning a vertical layout manager and attaching
     * the customized StoreAdapter, passing along a click listener to handle store selection.
     */
    private void setupRecyclerView() {
        storesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        placeAdapter = new PlaceAdapter(gameStores, this::focusOnStore);
        storesRecyclerView.setAdapter(placeAdapter);
    }

    /**
     * Triggered by the Google Maps API when the map instance is fully initialized and ready
     * for interaction. Applies UI configurations and plots the store markers.
     *
     * @param googleMap A non-null instance of a GoogleMap associated with the MapFragment or MapView.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;

        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        addStoreMarkers();

        if (!gameStores.isEmpty()) {
            LatLng israelCenter = new LatLng(32.0853, 34.7818);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(israelCenter, 8));
        }
    }

    /**
     * Iterates through the stored list of locations, creates Google Map markers for each,
     * and registers them in a local mapping structure for quick retrieval during interaction events.
     */
    private void addStoreMarkers() {
        if (googleMap == null) return;
        markerMap.clear();
        for (Place store : gameStores) {
            LatLng storeLocation = new LatLng(store.getLatitude(), store.getLongitude());

            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(storeLocation)
                    .title(store.getName())
                    .snippet(store.getAddress()));

            markerMap.put(String.valueOf(store.getId()), marker);
        }
    }

    /**
     * Toggles the visibility state between the list-based RecyclerView and the geographical MapView.
     * Updates the icon of the Floating Action Button to reflect the opposite view state.
     */
    private void toggleView() {
        showingMap = !showingMap;

        if (showingMap) {
            mapView.setVisibility(View.VISIBLE);
            storesRecyclerView.setVisibility(View.GONE);
            toggleFab.setImageResource(R.drawable.ic_store);
        } else {
            mapView.setVisibility(View.GONE);
            storesRecyclerView.setVisibility(View.VISIBLE);
            toggleFab.setImageResource(android.R.drawable.ic_dialog_map);
        }
    }

    /**
     * Animates the map's camera to focus on a specific store's coordinates and forces
     * its associated informational marker window to display. Switches the UI to map mode
     * if it is not currently active.
     *
     * @param store The Place object detailing the store to focus on.
     */
    private void focusOnStore(Place store) {
        if (!showingMap) {
            toggleView();
        }

        LatLng storeLocation = new LatLng(store.getLatitude(), store.getLongitude());
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(storeLocation, 15f), 1000, null);

        Marker marker = markerMap.get(String.valueOf(store.getId()));
        if (marker != null) {
            marker.showInfoWindow();
        }
    }

    /**
     * Called when the fragment is visible to the user. Delegates to the MapView lifecycle.
     */
    @Override
    public void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    /**
     * Called when the fragment is visible to the user and actively running. Delegates to the MapView lifecycle.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    /**
     * Called when the Fragment is no longer resumed. Delegates to the MapView lifecycle.
     */
    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    /**
     * Called when the Fragment is no longer started. Delegates to the MapView lifecycle.
     */
    @Override
    public void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
    }

    /**
     * Called when the view previously created by onCreateView has been detached from the fragment.
     * Delegates to the MapView lifecycle to prevent memory leaks.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) mapView.onDestroy();
    }

    /**
     * Called when the overall system is running low on memory. Delegates to the MapView lifecycle
     * to allow it to clear its internal memory caches.
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }
}