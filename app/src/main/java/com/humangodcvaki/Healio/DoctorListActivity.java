package com.humangodcvaki.Healio;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class DoctorListActivity extends AppCompatActivity {

    private RecyclerView recyclerViewDoctors;
    private ProgressBar progressBar;
    private TextView tvNoData;
    private DoctorAdapter adapter;
    private List<Doctor> doctorList;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_list);

        recyclerViewDoctors = findViewById(R.id.recyclerViewDoctors);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        doctorList = new ArrayList<>();

        recyclerViewDoctors.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DoctorAdapter(doctorList);
        recyclerViewDoctors.setAdapter(adapter);

        loadDoctors();
    }

    private void loadDoctors() {
        progressBar.setVisibility(View.VISIBLE);

        mDatabase.child("users").orderByChild("userType").equalTo("doctor")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressBar.setVisibility(View.GONE);
                        doctorList.clear();

                        for (DataSnapshot doctorSnapshot : snapshot.getChildren()) {
                            String name = doctorSnapshot.child("name").getValue(String.class);
                            String specialization = doctorSnapshot.child("specialization").getValue(String.class);
                            String hospital = doctorSnapshot.child("hospitalName").getValue(String.class);
                            String experience = doctorSnapshot.child("yearsExperience").getValue(String.class);
                            String phone = doctorSnapshot.child("phone").getValue(String.class);
                            String doctorId = doctorSnapshot.getKey();

                            Doctor doctor = new Doctor(doctorId, name, specialization,
                                    hospital, experience, phone);
                            doctorList.add(doctor);
                        }

                        if (doctorList.isEmpty()) {
                            tvNoData.setVisibility(View.VISIBLE);
                        } else {
                            tvNoData.setVisibility(View.GONE);
                        }

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(DoctorListActivity.this,
                                "Failed to load doctors", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Doctor Model Class
    static class Doctor {
        String id;
        String name;
        String specialization;
        String hospital;
        String experience;
        String phone;

        Doctor(String id, String name, String specialization, String hospital,
               String experience, String phone) {
            this.id = id;
            this.name = name;
            this.specialization = specialization;
            this.hospital = hospital;
            this.experience = experience;
            this.phone = phone;
        }
    }

    // RecyclerView Adapter
    class DoctorAdapter extends RecyclerView.Adapter<DoctorAdapter.DoctorViewHolder> {
        private List<Doctor> doctors;

        DoctorAdapter(List<Doctor> doctors) {
            this.doctors = doctors;
        }

        @NonNull
        @Override
        public DoctorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_doctor, parent, false);
            return new DoctorViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DoctorViewHolder holder, int position) {
            Doctor doctor = doctors.get(position);
            holder.bind(doctor);
        }

        @Override
        public int getItemCount() {
            return doctors.size();
        }

        class DoctorViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvSpecialization, tvHospital, tvExperience;
            Button btnBook;

            DoctorViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvDoctorName);
                tvSpecialization = itemView.findViewById(R.id.tvSpecialization);
                tvHospital = itemView.findViewById(R.id.tvHospital);
                tvExperience = itemView.findViewById(R.id.tvExperience);
                btnBook = itemView.findViewById(R.id.btnBookDoctor);
            }

            void bind(Doctor doctor) {
                tvName.setText("Dr. " + doctor.name);
                tvSpecialization.setText("Specialization: " + doctor.specialization);
                tvHospital.setText("Hospital: " + doctor.hospital);
                tvExperience.setText("Experience: " + doctor.experience + " years");

                btnBook.setOnClickListener(v -> {
                    // TODO: Implement booking functionality
                    Toast.makeText(itemView.getContext(),
                            "Booking appointment with Dr. " + doctor.name,
                            Toast.LENGTH_SHORT).show();
                });
            }
        }
    }
}