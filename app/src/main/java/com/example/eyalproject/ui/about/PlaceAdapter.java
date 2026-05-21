package com.example.eyalproject.ui.about;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eyalproject.Models.Place;
import com.example.eyalproject.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * RecyclerView adapter responsible for displaying a list of places.
 * Each item presents place information such as name, address,
 * and phone number, along with actions for calling and navigation.
 */
public class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.PlaceViewHolder> {

    /**
     * Collection of places displayed inside the RecyclerView.
     */
    private final List<Place> placeList;

    /**
     * Listener used to handle place selection events.
     */
    private final OnPlaceSelectedListener clickListener;

    /**
     * Callback interface triggered when a place item is pressed.
     */
    public interface OnPlaceSelectedListener {

        /**
         * Invoked when the user selects a place item.
         *
         * @param selectedPlace The selected place object.
         */
        void onPlaceSelected(Place selectedPlace);
    }

    /**
     * Creates a new PlaceAdapter instance.
     *
     * @param placeList     List of places to display.
     * @param clickListener Listener for item click actions.
     */
    public PlaceAdapter(List<Place> placeList,
                        OnPlaceSelectedListener clickListener) {

        this.placeList = placeList;
        this.clickListener = clickListener;
    }

    /**
     * Creates a new ViewHolder instance for a place item.
     *
     * @param parent   Parent ViewGroup.
     * @param viewType Type of the view.
     * @return Configured PlaceViewHolder instance.
     */
    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View itemLayout = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_store, parent, false);

        return new PlaceViewHolder(itemLayout);
    }
    /**
     * Binds place data to a specific RecyclerView item.
     * @param holder   Current ViewHolder.
     * @param position Current item position.
     */
    @Override
    public void onBindViewHolder(
            @NonNull PlaceViewHolder holder,
            int position
    ) {
        Place currentPlace = placeList.get(position);
        holder.displayPlaceData(currentPlace, clickListener);
    }

    /**
     * Returns the total number of places.
     *
     * @return Total item count.
     */
    @Override
    public int getItemCount() {
        return placeList.size();
    }

    /**
     * ViewHolder representing a single place item view.
     */
    static class PlaceViewHolder extends RecyclerView.ViewHolder {

        /**
         * UI components for displaying place information.
         */
        private final TextView placeNameText;
        private final TextView placeAddressText;
        private final TextView placePhoneText;

        /**
         * Action buttons for communication and navigation.
         */
        private final MaterialButton phoneCallButton;
        private final MaterialButton navigationButton;

        /**
         * Initializes all UI components for the place item.
         *
         * @param itemView Root item view.
         */
        public PlaceViewHolder(@NonNull View itemView) {
            super(itemView);

            placeNameText = itemView.findViewById(R.id.storeName);
            placeAddressText = itemView.findViewById(R.id.storeAddress);
            placePhoneText = itemView.findViewById(R.id.storePhone);
            phoneCallButton = itemView.findViewById(R.id.callButton);
            navigationButton = itemView.findViewById(R.id.directionsButton);
        }

        /**
         * Displays place information and configures button actions.
         *
         * @param currentPlace  Place object to display.
         * @param clickListener Listener for place selection.
         */
        public void displayPlaceData(
                final Place currentPlace,
                final OnPlaceSelectedListener clickListener
        ) {

            placeNameText.setText(currentPlace.getName());
            placeAddressText.setText(currentPlace.getAddress());
            placePhoneText.setText(currentPlace.getPhoneNumber());

            itemView.setOnClickListener(view ->
                    clickListener.onPlaceSelected(currentPlace));

            setupPhoneCallAction(currentPlace);
            setupNavigationAction(currentPlace);
        }

        /**
         * Configures the phone dial action button.
         *
         * @param currentPlace Selected place.
         */
        private void setupPhoneCallAction(final Place currentPlace) {

            phoneCallButton.setOnClickListener(view -> {

                Intent dialIntent = new Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse("tel:" + currentPlace.getPhoneNumber())
                );

                itemView.getContext().startActivity(dialIntent);
            });
        }

        /**
         * Configures the Google Maps navigation button.
         *
         * @param currentPlace Selected place.
         */
        private void setupNavigationAction(final Place currentPlace) {

            navigationButton.setOnClickListener(view -> {

                Uri locationUri = Uri.parse(
                        "geo:0,0?q="
                                + currentPlace.getLatitude()
                                + ","
                                + currentPlace.getLongitude()
                                + "("
                                + Uri.encode(currentPlace.getName())
                                + ")"
                );

                Intent navigationIntent =
                        new Intent(Intent.ACTION_VIEW, locationUri);

                navigationIntent.setPackage(
                        "com.google.android.apps.maps"
                );

                if (navigationIntent.resolveActivity(
                        itemView.getContext()
                                .getPackageManager()) != null) {

                    itemView.getContext()
                            .startActivity(navigationIntent);
                }
            });
        }
    }
}