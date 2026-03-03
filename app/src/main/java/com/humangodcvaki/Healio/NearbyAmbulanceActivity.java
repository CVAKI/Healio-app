package com.humangodcvaki.Healio;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class NearbyAmbulanceActivity extends AppCompatActivity {

    private RecyclerView recyclerViewAmbulances;
    private ProgressBar progressBar;
    private TextView tvNoData, tvLocationInfo;
    private AmbulanceAdapter adapter;
    private List<AmbulanceDriver> ambulanceList;
    private DatabaseReference mDatabase;

    private double userLatitude;
    private double userLongitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_ambulance);

        // Get user location from intent
        userLatitude = getIntent().getDoubleExtra("latitude", 0.0);
        userLongitude = getIntent().getDoubleExtra("longitude", 0.0);

        mDatabase = FirebaseDatabase.getInstance().getReference();

        recyclerViewAmbulances = findViewById(R.id.recyclerViewAmbulances);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        tvLocationInfo = findViewById(R.id.tvLocationInfo);

        tvLocationInfo.setText(String.format("📍 Your Location: %.4f, %.4f", userLatitude, userLongitude));

        ambulanceList = new ArrayList<>();
        recyclerViewAmbulances.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AmbulanceAdapter(ambulanceList);
        recyclerViewAmbulances.setAdapter(adapter);

        loadNearbyAmbulances();
    }

    private void loadNearbyAmbulances() {
        progressBar.setVisibility(View.VISIBLE);

        mDatabase.child("users").orderByChild("userType").equalTo("ambulance_driver")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressBar.setVisibility(View.GONE);
                        ambulanceList.clear();

                        for (DataSnapshot driverSnapshot : snapshot.getChildren()) {
                            String driverId = driverSnapshot.getKey();
                            String name = driverSnapshot.child("name").getValue(String.class);
                            String phone = driverSnapshot.child("phone").getValue(String.class);
                            String ambulanceNumber = driverSnapshot.child("ambulanceNumber").getValue(String.class);
                            String place = driverSnapshot.child("place").getValue(String.class);
                            Boolean available = driverSnapshot.child("available").getValue(Boolean.class);
                            Double lat = driverSnapshot.child("latitude").getValue(Double.class);
                            Double lng = driverSnapshot.child("longitude").getValue(Double.class);

                            // Only show available ambulances
                            if (available != null && available) {
                                // Calculate distance if location is available
                                double distance = 0.0;
                                if (lat != null && lng != null && userLatitude != 0.0 && userLongitude != 0.0) {
                                    distance = calculateDistance(userLatitude, userLongitude, lat, lng);
                                }

                                AmbulanceDriver driver = new AmbulanceDriver(
                                        driverId, name, phone, ambulanceNumber, place,
                                        distance, lat, lng
                                );
                                ambulanceList.add(driver);
                            }
                        }

                        // Sort by distance (closest first)
                        Collections.sort(ambulanceList, new Comparator<AmbulanceDriver>() {
                            @Override
                            public int compare(AmbulanceDriver a1, AmbulanceDriver a2) {
                                return Double.compare(a1.distance, a2.distance);
                            }
                        });

                        if (ambulanceList.isEmpty()) {
                            tvNoData.setVisibility(View.VISIBLE);
                            tvNoData.setText("No available ambulances found nearby.\n\n🚑 Please try again later or call emergency services.");
                        } else {
                            tvNoData.setVisibility(View.GONE);
                        }

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(NearbyAmbulanceActivity.this,
                                "Failed to load ambulances", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula to calculate distance in kilometers
        double earthRadius = 6371; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    // Model class
    static class AmbulanceDriver {
        String id;
        String name;
        String phone;
        String ambulanceNumber;
        String place;
        double distance;
        Double latitude;
        Double longitude;

        AmbulanceDriver(String id, String name, String phone, String ambulanceNumber,
                        String place, double distance, Double latitude, Double longitude) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.ambulanceNumber = ambulanceNumber;
            this.place = place;
            this.distance = distance;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    // Adapter
    class AmbulanceAdapter extends RecyclerView.Adapter<AmbulanceAdapter.AmbulanceViewHolder> {
        private List<AmbulanceDriver> drivers;

        AmbulanceAdapter(List<AmbulanceDriver> drivers) {
            this.drivers = drivers;
        }

        @NonNull
        @Override
        public AmbulanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ambulance, parent, false);
            return new AmbulanceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AmbulanceViewHolder holder, int position) {
            holder.bind(drivers.get(position));
        }

        @Override
        public int getItemCount() {
            return drivers.size();
        }

        class AmbulanceViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvAmbulanceNumber, tvPlace, tvDistance;
            Button btnCall, btnNavigate;

            AmbulanceViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvDriverName);
                tvAmbulanceNumber = itemView.findViewById(R.id.tvAmbulanceNumber);
                tvPlace = itemView.findViewById(R.id.tvPlace);
                tvDistance = itemView.findViewById(R.id.tvDistance);
                btnCall = itemView.findViewById(R.id.btnCall);
                btnNavigate = itemView.findViewById(R.id.btnNavigate);
            }

            void bind(AmbulanceDriver driver) {
                tvName.setText("Driver: " + driver.name);
                tvAmbulanceNumber.setText("🚑 " + driver.ambulanceNumber);
                tvPlace.setText("📍 " + driver.place);

                if (driver.distance > 0) {
                    tvDistance.setText(String.format("Distance: %.2f km", driver.distance));
                    tvDistance.setVisibility(View.VISIBLE);
                } else {
                    tvDistance.setVisibility(View.GONE);
                }

                // Call button
                btnCall.setOnClickListener(v -> {
                    if (ActivityCompat.checkSelfPermission(itemView.getContext(),
                            Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        Intent intent = new Intent(Intent.ACTION_CALL);
                        intent.setData(Uri.parse("tel:" + driver.phone));
                        itemView.getContext().startActivity(intent);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_DIAL);
                        intent.setData(Uri.parse("tel:" + driver.phone));
                        itemView.getContext().startActivity(intent);
                    }
                });

                // Navigate button
                btnNavigate.setOnClickListener(v -> {
                    if (driver.latitude != null && driver.longitude != null) {
                        String uri = String.format("google.navigation:q=%f,%f",
                                driver.latitude, driver.longitude);
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                        intent.setPackage("com.google.android.apps.maps");

                        if (intent.resolveActivity(itemView.getContext().getPackageManager()) != null) {
                            itemView.getContext().startActivity(intent);
                        } else {
                            // Fallback to browser maps
                            String mapsUrl = String.format("https://www.google.com/maps/dir/?api=1&destination=%f,%f",
                                    driver.latitude, driver.longitude);
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl));
                            itemView.getContext().startActivity(browserIntent);
                        }
                    } else {
                        Toast.makeText(itemView.getContext(),
                                "Location not available for this ambulance",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
}