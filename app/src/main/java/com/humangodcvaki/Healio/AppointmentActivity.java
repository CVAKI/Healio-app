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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppointmentActivity extends AppCompatActivity {

    private RecyclerView recyclerViewAppointments;
    private ProgressBar progressBar;
    private TextView tvNoAppointments;
    private FloatingActionButton fabNewAppointment;

    private AppointmentAdapter adapter;
    private List<Appointment> appointmentList;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointment);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        userId = mAuth.getCurrentUser().getUid();

        initializeViews();
        loadAppointments();
    }

    private void initializeViews() {
        recyclerViewAppointments = findViewById(R.id.recyclerViewAppointments);
        progressBar = findViewById(R.id.progressBar);
        tvNoAppointments = findViewById(R.id.tvNoAppointments);
        fabNewAppointment = findViewById(R.id.fabNewAppointment);

        appointmentList = new ArrayList<>();
        adapter = new AppointmentAdapter(appointmentList);
        recyclerViewAppointments.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewAppointments.setAdapter(adapter);

        fabNewAppointment.setOnClickListener(v -> {
            // Navigate to doctor list to book appointment
            startActivity(new android.content.Intent(this, DoctorListActivity.class));
        });
    }

    private void loadAppointments() {
        progressBar.setVisibility(View.VISIBLE);

        mDatabase.child("appointments")
                .orderByChild("patientId")
                .equalTo(userId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressBar.setVisibility(View.GONE);
                        appointmentList.clear();

                        for (DataSnapshot appointmentSnapshot : snapshot.getChildren()) {
                            String appointmentId = appointmentSnapshot.getKey();
                            String doctorId = appointmentSnapshot.child("doctorId").getValue(String.class);
                            String doctorName = appointmentSnapshot.child("doctorName").getValue(String.class);
                            String specialization = appointmentSnapshot.child("specialization").getValue(String.class);
                            String date = appointmentSnapshot.child("date").getValue(String.class);
                            String time = appointmentSnapshot.child("time").getValue(String.class);
                            String status = appointmentSnapshot.child("status").getValue(String.class);
                            Long timestamp = appointmentSnapshot.child("timestamp").getValue(Long.class);
                            String notes = appointmentSnapshot.child("notes").getValue(String.class);

                            Appointment appointment = new Appointment(
                                    appointmentId, doctorId, doctorName, specialization,
                                    date, time, status, timestamp, notes
                            );
                            appointmentList.add(appointment);
                        }

                        if (appointmentList.isEmpty()) {
                            tvNoAppointments.setVisibility(View.VISIBLE);
                            recyclerViewAppointments.setVisibility(View.GONE);
                        } else {
                            tvNoAppointments.setVisibility(View.GONE);
                            recyclerViewAppointments.setVisibility(View.VISIBLE);
                        }

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(AppointmentActivity.this,
                                "Failed to load appointments", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Appointment Model
    static class Appointment {
        String id;
        String doctorId;
        String doctorName;
        String specialization;
        String date;
        String time;
        String status; // pending, confirmed, completed, cancelled
        Long timestamp;
        String notes;

        Appointment(String id, String doctorId, String doctorName, String specialization,
                    String date, String time, String status, Long timestamp, String notes) {
            this.id = id;
            this.doctorId = doctorId;
            this.doctorName = doctorName;
            this.specialization = specialization;
            this.date = date;
            this.time = time;
            this.status = status != null ? status : "pending";
            this.timestamp = timestamp;
            this.notes = notes;
        }
    }

    // Adapter
    class AppointmentAdapter extends RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder> {
        private List<Appointment> appointments;
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        AppointmentAdapter(List<Appointment> appointments) {
            this.appointments = appointments;
        }

        @NonNull
        @Override
        public AppointmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_appointment, parent, false);
            return new AppointmentViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AppointmentViewHolder holder, int position) {
            holder.bind(appointments.get(position));
        }

        @Override
        public int getItemCount() {
            return appointments.size();
        }

        class AppointmentViewHolder extends RecyclerView.ViewHolder {
            TextView tvDoctorName, tvSpecialization, tvDateTime, tvStatus, tvNotes;
            Button btnCancel, btnReschedule, btnViewDetails;

            AppointmentViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDoctorName = itemView.findViewById(R.id.tvDoctorName);
                tvSpecialization = itemView.findViewById(R.id.tvSpecialization);
                tvDateTime = itemView.findViewById(R.id.tvDateTime);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvNotes = itemView.findViewById(R.id.tvNotes);
                btnCancel = itemView.findViewById(R.id.btnCancel);
                btnReschedule = itemView.findViewById(R.id.btnReschedule);
                btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
            }

            void bind(Appointment appointment) {
                tvDoctorName.setText("Dr. " + appointment.doctorName);
                tvSpecialization.setText(appointment.specialization);
                tvDateTime.setText(appointment.date + " at " + appointment.time);

                // Status styling
                tvStatus.setText(appointment.status.toUpperCase());
                switch (appointment.status.toLowerCase()) {
                    case "confirmed":
                        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        break;
                    case "pending":
                        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                        break;
                    case "cancelled":
                        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        btnCancel.setVisibility(View.GONE);
                        btnReschedule.setVisibility(View.GONE);
                        break;
                    case "completed":
                        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                        btnCancel.setVisibility(View.GONE);
                        btnReschedule.setVisibility(View.GONE);
                        break;
                }

                if (appointment.notes != null && !appointment.notes.isEmpty()) {
                    tvNotes.setVisibility(View.VISIBLE);
                    tvNotes.setText("Notes: " + appointment.notes);
                } else {
                    tvNotes.setVisibility(View.GONE);
                }

                // Cancel button
                btnCancel.setOnClickListener(v -> showCancelDialog(appointment));

                // Reschedule button
                btnReschedule.setOnClickListener(v -> {
                    Toast.makeText(itemView.getContext(),
                            "Reschedule feature coming soon!", Toast.LENGTH_SHORT).show();
                });

                // View details button
                btnViewDetails.setOnClickListener(v -> showAppointmentDetails(appointment));
            }
        }
    }

    private void showCancelDialog(Appointment appointment) {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Appointment")
                .setMessage("Are you sure you want to cancel this appointment with Dr. "
                        + appointment.doctorName + "?")
                .setPositiveButton("Yes, Cancel", (dialog, which) -> cancelAppointment(appointment))
                .setNegativeButton("No", null)
                .show();
    }

    private void cancelAppointment(Appointment appointment) {
        mDatabase.child("appointments").child(appointment.id).child("status")
                .setValue("cancelled")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Appointment cancelled", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to cancel appointment", Toast.LENGTH_SHORT).show();
                });
    }

    private void showAppointmentDetails(Appointment appointment) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
        String formattedDate = appointment.timestamp != null
                ? dateFormat.format(new Date(appointment.timestamp))
                : appointment.date + " at " + appointment.time;

        new AlertDialog.Builder(this)
                .setTitle("Appointment Details")
                .setMessage(
                        "Doctor: Dr. " + appointment.doctorName + "\n" +
                                "Specialization: " + appointment.specialization + "\n" +
                                "Date & Time: " + formattedDate + "\n" +
                                "Status: " + appointment.status.toUpperCase() + "\n" +
                                (appointment.notes != null && !appointment.notes.isEmpty()
                                        ? "Notes: " + appointment.notes
                                        : "")
                )
                .setPositiveButton("OK", null)
                .setNeutralButton("Contact Doctor", (dialog, which) -> {
                    // Open chat with doctor
                    android.content.Intent intent = new android.content.Intent(this, ChatActivity.class);
                    intent.putExtra("doctorId", appointment.doctorId);
                    intent.putExtra("doctorName", appointment.doctorName);
                    startActivity(intent);
                })
                .show();
    }
}