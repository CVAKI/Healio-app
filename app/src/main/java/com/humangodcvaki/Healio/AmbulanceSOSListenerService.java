package com.humangodcvaki.Healio;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Background foreground-service that:
 *   1. Listens for emergency_ambulance notifications for the logged-in driver.
 *   2. Fetches authoritative alert data from emergencyAlerts (same pattern as SOSListenerService).
 *   3. Periodically updates the driver's latitude/longitude in Firebase.
 */
public class AmbulanceSOSListenerService extends Service {

    private static final String TAG        = "AmbulanceSOSListener";
    private static final String CHANNEL_ID = "AMBULANCE_SOS_CHANNEL";
    private static final int    NOTIF_ID   = 3001;

    private static final long LOCATION_INTERVAL_MS = 60_000L;

    private FirebaseAuth      mAuth;
    private DatabaseReference mDatabase;
    private ChildEventListener notifListener;
    private String             userId;

    // Track processed alerts to prevent duplicates — same as SOSListenerService
    private final Set<String> processedAlerts    = new HashSet<>();
    private static String     currentActiveAlert = null;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback            locationCallback;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AmbulanceSOSListenerService created");

        mAuth     = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        createNotificationChannel();
        startForeground(NOTIF_ID, buildForegroundNotification());

        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
            startListening();
            setupAndStartLocationUpdates();
        } else {
            Log.e(TAG, "No user logged in — stopping service");
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (notifListener != null && userId != null) {
            mDatabase.child("notifications").child(userId)
                    .removeEventListener(notifListener);
        }
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        processedAlerts.clear();
        currentActiveAlert = null;
        Log.d(TAG, "AmbulanceSOSListenerService destroyed");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Task removed, but service continues");
    }

    // -----------------------------------------------------------------------
    // Notification channel + foreground notification
    // -----------------------------------------------------------------------
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Ambulance SOS Monitoring",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Listens for emergency dispatch alerts");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildForegroundNotification() {
        Intent tapIntent = new Intent(this, AmbulanceDriverDashboardActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🚑 Ambulance Dispatch Active")
                .setContentText("Listening for emergency calls…")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pi)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    // -----------------------------------------------------------------------
    // Firebase SOS listener — mirrors SOSListenerService exactly
    // -----------------------------------------------------------------------
    private void startListening() {
        notifListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String prev) {
                if (!snapshot.exists()) return;

                String  type = snapshot.child("type").getValue(String.class);
                Boolean read = snapshot.child("read").getValue(Boolean.class);

                Log.d(TAG, "Notification received — type: " + type + ", read: " + read);

                if ("emergency_ambulance".equals(type) && (read == null || !read)) {
                    String alertId = snapshot.child("alertId").getValue(String.class);

                    if (alertId != null && processedAlerts.contains(alertId)) {
                        Log.d(TAG, "Alert " + alertId + " already processed, skipping");
                        return;
                    }

                    Log.d(TAG, "Ambulance SOS detected! Alert ID: " + alertId);

                    // Mark read=true immediately so the service never replays on restart
                    // — same approach as SOSListenerService
                    snapshot.getRef().child("read").setValue(true);

                    if (alertId != null) processedAlerts.add(alertId);

                    String notifKey = snapshot.getKey();

                    // Fetch authoritative data from emergencyAlerts (not the notification node)
                    loadAlertAndShowIncomingScreen(alertId, notifKey);
                }
            }

            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError e) {
                Log.e(TAG, "Listener cancelled: " + e.getMessage());
            }
        };

        // BUG FIX: removed .orderByChild("read").equalTo(false) from the query.
        // Firebase requires a manual index ("read" in database.rules.json) for
        // orderByChild queries. Without the index the SDK silently returns zero
        // results on many devices, so the driver never receives the alert.
        // We now listen to ALL notifications for this user and filter in Java.
        mDatabase.child("notifications").child(userId)
                .addChildEventListener(notifListener);

        Log.d(TAG, "Listening for ambulance SOS alerts for user: " + userId);
    }

    // -----------------------------------------------------------------------
    // Load alert details + trigger ringtone + show incoming screen
    // Identical pattern to SOSListenerService#loadAlertAndShowIncomingScreen
    // -----------------------------------------------------------------------
    private void loadAlertAndShowIncomingScreen(String alertId, String notifKey) {
        if (alertId == null) {
            Log.e(TAG, "Alert ID is null");
            return;
        }

        mDatabase.child("emergencyAlerts").child(alertId)
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            Log.w(TAG, "Alert " + alertId + " does not exist");
                            processedAlerts.remove(alertId);
                            return;
                        }

                        String status = snapshot.child("status").getValue(String.class);
                        Log.d(TAG, "Ambulance alert status: " + status);

                        if (!"active".equals(status)) {
                            Log.d(TAG, "Alert is not active, ignoring");
                            processedAlerts.remove(alertId);
                            if (alertId.equals(currentActiveAlert)) currentActiveAlert = null;
                            return;
                        }

                        if (alertId.equals(currentActiveAlert)) {
                            Log.d(TAG, "Alert " + alertId + " already showing, skipping");
                            return;
                        }
                        currentActiveAlert = alertId;

                        // Read values from the authoritative emergencyAlerts node
                        String patientName  = snapshot.child("patientName").getValue(String.class);
                        String patientPhone = snapshot.child("patientPhone").getValue(String.class);
                        Double lat          = snapshot.child("latitude").getValue(Double.class);
                        Double lng          = snapshot.child("longitude").getValue(Double.class);
                        Double distance     = snapshot.child("distance").getValue(Double.class);

                        double latVal  = lat      != null ? lat      : 0.0;
                        double lngVal  = lng      != null ? lng      : 0.0;
                        double distVal = distance != null ? distance : 0.0;

                        Log.d(TAG, "Starting ringtone + ambulance incoming screen for: " + patientName);

                        // 1. Start ringtone service
                        Intent ringtone = new Intent(AmbulanceSOSListenerService.this,
                                SOSRingtoneService.class);
                        ringtone.putExtra("alertId",      alertId);
                        ringtone.putExtra("patientName",  patientName);
                        ringtone.putExtra("patientPhone", patientPhone);
                        ringtone.putExtra("latitude",     latVal);
                        ringtone.putExtra("longitude",    lngVal);
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                startForegroundService(ringtone);
                            else
                                startService(ringtone);
                        } catch (Exception e) {
                            Log.e(TAG, "Ringtone start error: " + e.getMessage());
                        }

                        // 2. Launch IncomingAmbulanceSOSActivity after 500 ms delay
                        //    (same guard delay used in SOSListenerService)
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                Intent ui = new Intent(AmbulanceSOSListenerService.this,
                                        IncomingAmbulanceSOSActivity.class);
                                ui.putExtra("alertId",         alertId);
                                ui.putExtra("notificationKey", notifKey);
                                ui.putExtra("patientName",     patientName);
                                ui.putExtra("patientPhone",    patientPhone);
                                ui.putExtra("latitude",        latVal);
                                ui.putExtra("longitude",       lngVal);
                                ui.putExtra("distance",        distVal);
                                ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK  |
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP  |
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                startActivity(ui);
                            } catch (Exception e) {
                                Log.e(TAG, "UI launch error: " + e.getMessage());
                            }
                        }, 500);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Failed to load ambulance alert: " + error.getMessage());
                        processedAlerts.remove(alertId);
                    }
                });
    }

    // -----------------------------------------------------------------------
    // Background location tracking
    // -----------------------------------------------------------------------
    private void setupAndStartLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted — GPS updates skipped");
            return;
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                if (result.getLastLocation() == null) return;
                double lat = result.getLastLocation().getLatitude();
                double lng = result.getLastLocation().getLongitude();
                saveLocationToFirebase(lat, lng);
            }
        };

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, LOCATION_INTERVAL_MS)
                .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS / 2)
                .build();

        fusedLocationClient.requestLocationUpdates(
                request, locationCallback, Looper.getMainLooper());

        Log.d(TAG, "Background location updates started");
    }

    private void saveLocationToFirebase(double latitude, double longitude) {
        if (userId == null) return;
        if (latitude == 0.0 && longitude == 0.0) return; // reject invalid fix

        Map<String, Object> update = new HashMap<>();
        update.put("latitude", latitude);
        update.put("longitude", longitude);
        update.put("lastLocationUpdate", System.currentTimeMillis());

        mDatabase.child("users").child(userId)
                .updateChildren(update)
                .addOnSuccessListener(v ->
                        Log.d(TAG, "BG location saved: " + latitude + ", " + longitude))
                .addOnFailureListener(e ->
                        Log.e(TAG, "BG location save failed: " + e.getMessage()));
    }

    // -----------------------------------------------------------------------
    // Static helpers
    // -----------------------------------------------------------------------
    public static void clearCurrentAlert() {
        currentActiveAlert = null;
    }
}