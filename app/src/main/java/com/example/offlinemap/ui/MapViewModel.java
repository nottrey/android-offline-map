package com.example.offlinemap.ui;


import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.offlinemap.R;
import com.example.offlinemap.data.model.Marker;
import com.example.offlinemap.mapHandler;
import com.example.offlinemap.geofence.MarkerGeofenceReceiver;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;
import com.mapbox.maps.plugin.LocationPuck2D;

import com.mapbox.maps.plugin.annotation.AnnotationConfig;
import com.mapbox.maps.plugin.annotation.AnnotationPlugin;
import com.mapbox.maps.plugin.annotation.AnnotationPluginImplKt;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions;
import com.mapbox.maps.plugin.gestures.GesturesPlugin;
import com.mapbox.maps.plugin.gestures.GesturesUtils;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentUtils;
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener;
import com.mapbox.maps.plugin.viewport.ViewportPlugin;
import com.mapbox.maps.plugin.viewport.ViewportUtils;
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions;
import com.mapbox.maps.plugin.viewport.state.FollowPuckViewportState;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;


public class MapViewModel extends AppCompatActivity {
    private static final String NOTIFICATION_CHANNEL_ID = "marker_proximity";
    private static final int PROXIMITY_RADIUS_METERS = 500;
    private static final long NOTIFY_COOLDOWN_MS = 10 * 60 * 1000;

    private MapView mapView;
    private mapHandler mapHelper;
    private GeofencingClient geofencingClient;
    private Button backToGalleryBtn;
    PointAnnotationManager pointAnnotationManager;
    FirebaseFirestore database;
    FirebaseUser currentUser;
    private FirebaseAuth mAuth;
    boolean isDownloaded;
    double lat, lng;
    private Uri tempImageUri;
    private ImageView tempImageView;
    private final Map<String, Long> lastNotifiedAt = new HashMap<>();
    private boolean hasCenteredOnUser = false;
    private android.app.PendingIntent geofencePendingIntent;

    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    tempImageUri = uri;
                    if (tempImageView != null) {
                        tempImageView.setVisibility(View.VISIBLE);
                        tempImageView.setImageURI(uri);
                    }
                }
            });

    String id;

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }
    private String selectedCategory = "All";
    private List<Marker> allMarkers = new ArrayList<>(); // Keep a master list here

    private void setupCategoryFilters() {
        ChipGroup chipGroup = findViewById(R.id.categoryChipGroup);
        if (chipGroup == null) return; // Safety check to prevent "map closing" crash

        chipGroup.removeAllViews();
        List<String> uniqueCategories = new ArrayList<>();
        uniqueCategories.add("All");

        // Add default categories from arrays.xml
        String[] defaultCategories = getResources().getStringArray(R.array.marker_categories);
        for (String cat : defaultCategories) {
            if (!cat.equals("Custom") && !uniqueCategories.contains(cat)) {
                uniqueCategories.add(cat);
            }
        }

        // Add any custom categories from existing markers
        for (Marker m : allMarkers) {
            String cat = m.getCategory();
            if (cat != null && !cat.isEmpty() && !uniqueCategories.contains(cat)) {
                uniqueCategories.add(cat);
            }
        }

        for (String categoryName : uniqueCategories) {
            addChip(chipGroup, categoryName);
        }

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip chip = findViewById(checkedIds.get(0));
                if (chip != null) {
                    selectedCategory = chip.getText().toString();
                    applyFilter();
                }
            }
        });
    }

    private void addChip(ChipGroup group, String name) {
        Chip chip = new Chip(this);
        chip.setText(name);
        chip.setCheckable(true);
        chip.setClickable(true);
        group.addView(chip);
    }

    private void applyFilter() {
        if (pointAnnotationManager == null) return;

        pointAnnotationManager.deleteAll();

        for (Marker m : allMarkers) {
            if (selectedCategory.equals("All") || m.getCategory().equals(selectedCategory)) {
                createAnnotationFromMarker(m, m.id);
            }
        }
    }

    private void addMarkerToMap(Point point) {
        View customView = LayoutInflater.from(this).inflate(R.layout.add_marker, null);

        EditText etTitle = customView.findViewById(R.id.etMarkerTitle);
        EditText etDescription = customView.findViewById(R.id.etMarkerDescription);
        Spinner spCategory = customView.findViewById(R.id.spinnerCategory);
        EditText etCustomCategory = customView.findViewById(R.id.etCustomCategory);

        // FIX: Initialize the Spinner Adapter for new markers
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.marker_categories, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(adapter);

        tempImageView = customView.findViewById(R.id.imageView);
        android.widget.Button btnAddPhoto = customView.findViewById(R.id.btnSelectImage);

        btnAddPhoto.setOnClickListener(v -> {
            pickMedia.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                    .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                if (selected.equals("Custom")) {
                    etCustomCategory.setVisibility(View.VISIBLE);
                } else {
                    etCustomCategory.setVisibility(View.GONE);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(customView)
                .setPositiveButton("SAVE", (d, which) -> {
                    String title = etTitle.getText().toString();
                    String desc = etDescription.getText().toString();

                    Object selectedItem = spCategory.getSelectedItem();
                    String category;

                    if (selectedItem != null && selectedItem.toString().equals("Custom")) {
                        category = etCustomCategory.getText().toString().trim();
                        if (category.isEmpty()) category = "General";
                    } else {
                        category = (selectedItem != null) ? selectedItem.toString() : "General";
                    }

                    processMarkerSave(point, title, desc, category);
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void processMarkerSave(Point point, String title, String description, String category) {
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String finalTitle = (title == null || title.trim().isEmpty()) ? "Unnamed Location" : title.trim();
        String tempId = java.util.UUID.randomUUID().toString();

        Marker marker = new Marker(tempId, finalTitle, id, currentUser.getUid(), point.latitude(), point.longitude());
        marker.setDescription(description);
        marker.setCategory(category);

        if (tempImageUri != null) {
            String localPath = saveImageToInternalStorage(tempImageUri);
            marker.setInternalPath(localPath);
            tempImageUri = null;
        }

        allMarkers.add(marker);
        updateGeofences();

        PointAnnotation annotation = createAnnotationFromMarker(marker, tempId);
        if (annotation != null) {
            annotation.setData(new com.google.gson.JsonPrimitive(tempId));
            saveMarkerToDB(marker, annotation);
        }
    }

    private void saveMarkerToDB(Marker marker, PointAnnotation annotation) {
        database.collection("markers")
                .add(marker)
                .addOnSuccessListener(documentReference -> {
                    String firestoreId = documentReference.getId();
                    marker.id = firestoreId;
                    annotation.setData(new com.google.gson.JsonPrimitive(firestoreId));

                    if (marker.getInternalPath() != null) {
                        uploadSingleImage(marker);
                    }

                    if (isDownloaded) {
                        mapHelper.saveMarker(id, marker);
                    }

                    runOnUiThread(() -> {
                        setupCategoryFilters();
                        applyFilter();
                        updateGeofences();
                    });
                })
                .addOnFailureListener(e -> Log.e("Firestore", "Failed to save marker", e));
    }

    private void uploadSingleImage(Marker marker) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        File file = new File(marker.getInternalPath());
        Uri fileUri = Uri.fromFile(file);

        StorageReference ref = storage.getReference().child("marker_images/" + marker.id + ".jpg");

        ref.putFile(fileUri).addOnSuccessListener(taskSnapshot -> {
            ref.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadUrl = uri.toString();

                marker.setImageUrl(downloadUrl);

                database.collection("markers").document(marker.id)
                        .update("imageUrl", downloadUrl)
                        .addOnSuccessListener(aVoid -> Log.d("Firebase", "Firestore URL updated!"))
                        .addOnFailureListener(e -> Log.e("Firebase", "Failed to update URL in Firestore", e));

            });
        }).addOnFailureListener(e -> Log.e("Firebase", "Storage upload failed", e));
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mapHelper = new mapHandler(this);

        id = getIntent().getStringExtra("id");
        lat = getIntent().getDoubleExtra("lat", 0.0);
        lng = getIntent().getDoubleExtra("lng", 0.0);
        isDownloaded = getIntent().getBooleanExtra("downloaded", false);

        database = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.mapView);
        backToGalleryBtn = findViewById(R.id.btnBackToGallery);
        mapHelper = new mapHandler(this);
        geofencingClient = LocationServices.getGeofencingClient(this);

        if (backToGalleryBtn != null) {
            backToGalleryBtn.setOnClickListener(v -> finish());
        }

        createNotificationChannel();
        ensureBackgroundLocationPermission();

        setUpPlugins();

        if (isDownloaded) {
            displayOfflineMap();
            showLocation();
        } else {
            if (id != null && id.equals("current_location")) {
                showLocation();
            } else if (lat != 0.0 && lng != 0.0) {
                Point myPoint = Point.fromLngLat(lng, lat);
                showRegion(myPoint);
                showLocation();
            } else {
                showLocation();
            }
        }
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager =
                (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            android.net.NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());

            return capabilities != null && (
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
    }

    private void loadMarkers() {
        allMarkers.clear();
        if (pointAnnotationManager == null) return;
        pointAnnotationManager.deleteAll();

        if (isDownloaded) {
            ArrayList<Marker> localMarkers = mapHelper.getSavedMarkersForRegion(id);
            for (Marker m : localMarkers) {
                if (!allMarkers.contains(m)) {
                    allMarkers.add(m);
                }
            }
        }
        if (isNetworkAvailable()) {
            uploadPendingImages();
        }

        database.collection("markers")
                .whereEqualTo("mapId", id)
                .whereEqualTo("uid", currentUser.getUid())
                .get()
                .addOnSuccessListener(task -> {
                    for (QueryDocumentSnapshot doc : task) {
                        Marker m = doc.toObject(Marker.class);
                        m.id = doc.getId();
                        allMarkers.add(m);
                    }
                    setupCategoryFilters();
                    applyFilter();
                    updateGeofences();
                });
    }

    private PointAnnotation createAnnotationFromMarker(Marker marker, String docId) {
        String iconKey = "icon-blue";

        if (marker.getCategory() != null) {
            String cat = marker.getCategory().toLowerCase();
            if (cat.contains("favorites")) iconKey = "icon-red";
            else if (cat.contains("work")) iconKey = "icon-blue";
            else if (cat.contains("food")) iconKey = "icon-yellow";
            else iconKey = "icon-green"; // custom
        }

        PointAnnotationOptions options = new PointAnnotationOptions()
                .withPoint(Point.fromLngLat(marker.getLng(), marker.getLat()))
                .withIconImage(iconKey)
                .withTextField(marker.getName())
                .withIconSize(1.5);

        PointAnnotation annotation = pointAnnotationManager.create(options);
        annotation.setData(new com.google.gson.JsonPrimitive(docId));
        return annotation;
    }

    private Bitmap getColoredMarkerBitmap(int color) {
        Drawable drawable = AppCompatResources.getDrawable(this, R.drawable.baseline_location_on_24);
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.setTint(color);
        drawable.draw(canvas);
        return bitmap;
    }

    private void setUpPlugins() {
        AnnotationPlugin annotationPlugin = AnnotationPluginImplKt.getAnnotations(mapView);

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS, style -> {

            style.addImage("icon-red", getColoredMarkerBitmap(android.graphics.Color.RED));
            style.addImage("icon-blue", getColoredMarkerBitmap(android.graphics.Color.BLUE));
            style.addImage("icon-green", getColoredMarkerBitmap(android.graphics.Color.GREEN));
            style.addImage("icon-yellow", getColoredMarkerBitmap(android.graphics.Color.YELLOW));

            if (pointAnnotationManager == null && annotationPlugin != null) {
                pointAnnotationManager = PointAnnotationManagerKt.createPointAnnotationManager(
                        annotationPlugin, new AnnotationConfig());

                pointAnnotationManager.addClickListener(annotation -> {
                    showEditDialog(annotation);
                    return true;
                });
            }

            if (pointAnnotationManager != null) {
                pointAnnotationManager.deleteAll();
            }

            loadMarkers();

            GesturesPlugin gesturesPlugin = GesturesUtils.getGestures(mapView);
            gesturesPlugin.addOnMapLongClickListener(point -> {
                addMarkerToMap(point);
                return true;
            });
        });
    }

    private void showEditDialog(PointAnnotation annotation) {
        String docId = annotation.getData().getAsString();

        database.collection("markers").document(docId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    Marker marker = documentSnapshot.toObject(Marker.class);
                    if (marker != null) {
                        marker.id = docId;
                        showEditUI(annotation, marker);
                    } else {
                        readOfflineMarkers(docId, annotation);

                    }
                })
                .addOnFailureListener(e -> {

                });
    }

    private void readOfflineMarkers(String docId, PointAnnotation annotation){
        ArrayList<Marker> markers = mapHelper.getSavedMarkersForRegion(id);
        Marker localMarker = null;
        for (Marker m : markers) {
            if (m.id.equals(docId)) {
                localMarker = m;
                break;
            }
        }
        if (localMarker != null) {
            showEditUI(annotation, localMarker);
        }
    }

    private void showEditUI(PointAnnotation annotation, Marker existingMarker) {
        View editView = LayoutInflater.from(this).inflate(R.layout.add_marker, null);

        EditText etTitle = editView.findViewById(R.id.etMarkerTitle);
        EditText etDescription = editView.findViewById(R.id.etMarkerDescription);
        Spinner spCategory = editView.findViewById(R.id.spinnerCategory);
        EditText etCustomCategory = editView.findViewById(R.id.etCustomCategory);
        ImageView ivMarker = editView.findViewById(R.id.imageView);
        TextView btnRemovePhoto = editView.findViewById(R.id.removeImgBtn);
        android.widget.Button btnAddPhoto = editView.findViewById(R.id.btnSelectImage);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.marker_categories, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(adapter);

        boolean hasImage = (existingMarker.getInternalPath() != null || existingMarker.getImageUrl() != null);
        ivMarker.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        btnRemovePhoto.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        btnAddPhoto.setVisibility(hasImage ? View.GONE : View.VISIBLE);

        btnAddPhoto.setOnClickListener(v -> {
            tempImageView = ivMarker;
            pickMedia.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                    .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
            btnRemovePhoto.setVisibility(View.VISIBLE);
            btnAddPhoto.setVisibility(View.GONE);
            ivMarker.setVisibility(View.VISIBLE);
        });

        btnRemovePhoto.setOnClickListener(v -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Remove Photo?")
                    .setMessage("This will permanently delete the photo from this marker.")
                    .setPositiveButton("REMOVE", (dialogInterface, i) -> {
                        deleteImageFiles(existingMarker);
                        ivMarker.setImageURI(null);
                        ivMarker.setVisibility(View.GONE);
                        btnRemovePhoto.setVisibility(View.GONE);
                        btnAddPhoto.setVisibility(View.VISIBLE);
                        Toast.makeText(this, "Photo removed", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("CANCEL", null)
                    .show();
        });

        if (existingMarker != null) {
            etTitle.setText(existingMarker.getName());
            etDescription.setText(existingMarker.getDescription());

            if (existingMarker.getInternalPath() != null) {
                File file = new File(existingMarker.getInternalPath());
                if (file.exists()) {
                    ivMarker.setImageURI(android.net.Uri.fromFile(file));
                }
            } else if (existingMarker.getImageUrl() != null) {
                loadImageFromUrl(existingMarker.getImageUrl(), ivMarker);
            }

            String categoryValue = existingMarker.getCategory();
            if (categoryValue != null) {
                int position = adapter.getPosition(categoryValue);
                if (position >= 0) {
                    spCategory.setSelection(position);
                } else {
                    int customPos = adapter.getPosition("Custom");
                    spCategory.setSelection(customPos);
                    etCustomCategory.setVisibility(View.VISIBLE);
                    etCustomCategory.setText(categoryValue);
                }
            }
        }

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Edit Marker")
                .setView(editView)
                .setPositiveButton("UPDATE", (d, which) -> {
                    String newTitle = etTitle.getText().toString();
                    String newDesc = etDescription.getText().toString();
                    String selectedItem = spCategory.getSelectedItem().toString();

                    String newCat = selectedItem.equals("Custom")
                            ? etCustomCategory.getText().toString().trim()
                            : selectedItem;

                    if (newCat.isEmpty()) newCat = "General";

                    if (tempImageUri != null) {
                        String localPath = saveImageToInternalStorage(tempImageUri);
                        existingMarker.setInternalPath(localPath);
                        if (isNetworkAvailable()) uploadSingleImage(existingMarker);
                        tempImageUri = null;
                    }

                    updateMarker(annotation, newTitle, newDesc, newCat);
                })
                .setNeutralButton("CANCEL", null)
                .setNegativeButton("DELETE", (d, which) -> {
                    deleteImageFiles(existingMarker);
                    mapHelper.deleteMarker(existingMarker.id, existingMarker.getMapId());
                    allMarkers.removeIf(m -> m.id.equals(existingMarker.id));
                    pointAnnotationManager.delete(annotation);
                    applyFilter();
                    updateGeofences();
                })
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
    }

    private void loadImageFromUrl(String url, ImageView imageView) {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

        executor.execute(() -> {
            try {
                java.net.URL imageUrl = new java.net.URL(url);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) imageUrl.openConnection();
                connection.setDoInput(true);
                connection.connect();
                java.io.InputStream input = connection.getInputStream();
                android.graphics.Bitmap myBitmap = android.graphics.BitmapFactory.decodeStream(input);

                handler.post(() -> {
                    imageView.setVisibility(View.VISIBLE);
                    imageView.setImageBitmap(myBitmap);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateMarker(PointAnnotation annotation, String name, String desc, String cat) {
        String docId = annotation.getData().getAsString();
        Point point = annotation.getPoint();
        database.collection("markers").document(docId)
                .update("name", name,
                        "description", desc,
                        "category", cat);


        Marker updatedMarker = new Marker();
        updatedMarker.id = docId;
        updatedMarker.setName(name);
        updatedMarker.setDescription(desc);
        updatedMarker.setCategory(cat);
        updatedMarker.setLat(point.latitude());
        updatedMarker.setLng(point.longitude());
        updatedMarker.setUid(currentUser.getUid());

        for (int i = 0; i < allMarkers.size(); i++) {
            if (allMarkers.get(i).id.equals(docId)) {
                allMarkers.set(i, updatedMarker);
                break;
            }
        }
        updateGeofences();
        setupCategoryFilters();
        applyFilter();

        if (isDownloaded) {
            mapHelper.updateOrAddMarkerLocal(id, updatedMarker);
        }

        pointAnnotationManager.delete(annotation);
        createAnnotationFromMarker(updatedMarker, docId);
    }

    private void displayOfflineMap(){
        Log.d("MapDebug", "display offline map");
        mapHandler handler = new mapHandler(this);
        handler.loadSavedRegion(mAuth.getUid(), id, this.mapView);
    }

    private void deleteImageFiles(Marker marker){
        if (marker.getInternalPath() != null) {
            File file = new File(marker.getInternalPath());
            if (file.exists()) {
                file.delete();
            }
            marker.setInternalPath(null);
        }

        if (marker.getImageUrl() != null) {
            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference ref = storage.getReferenceFromUrl(marker.getImageUrl());

            ref.delete().addOnSuccessListener(aVoid -> {
                Log.d("Delete", "File deleted from Storage");
            }).addOnFailureListener(e -> {
                Log.e("Delete", "Failed to delete from Storage: " + e.getMessage());
            });

            marker.setImageUrl(null);
        }

        if (marker.id != null) {
            database.collection("markers").document(marker.id)
                    .update("imageUrl", null, "internalPath", null);
        }

        if (isDownloaded) {
            mapHelper.updateOrAddMarkerLocal(id, marker);
        }
    }

    void showLocation() {
        LocationComponentPlugin locationPlugin = LocationComponentUtils.getLocationComponent(mapView);

        locationPlugin.setEnabled(true);
        LocationPuck2D puck = new LocationPuck2D();
        puck.setTopImage(AppCompatResources.getDrawable(this, R.drawable.baseline_assistant_navigation_24));
        locationPlugin.setLocationPuck(puck);

        locationPlugin.addOnIndicatorPositionChangedListener(new OnIndicatorPositionChangedListener() {
            @Override
            public void onIndicatorPositionChanged(@NonNull Point userPoint) {

                boolean isCurrentLocationMap = "current_location".equals(id);

                boolean isInsideRegion = (lat != 0.0 && isUserInRegion(userPoint, lat, lng));

                if ((isCurrentLocationMap || isInsideRegion) && !hasCenteredOnUser) {

                    ViewportPlugin viewportPlugin = ViewportUtils.getViewport(mapView);
                    FollowPuckViewportState followPuckState = viewportPlugin.makeFollowPuckViewportState(
                            new FollowPuckViewportStateOptions.Builder().zoom(14.0).build()
                    );
                    viewportPlugin.transitionTo(followPuckState, null, null);
                    hasCenteredOnUser = true;

                    Log.d("MapDebug", "Tracking enabled because: " +
                            (isCurrentLocationMap ? "Current Location Map" : "User is in range"));
                }

                checkProximityAndNotify(userPoint);
            }
        });
    }

    private void checkProximityAndNotify(Point userPoint) {
        if (allMarkers == null || allMarkers.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (Marker marker : allMarkers) {
            if (marker == null) continue;
            if (marker.getLat() == 0.0 && marker.getLng() == 0.0) continue;

            float[] results = new float[1];
            android.location.Location.distanceBetween(
                    userPoint.latitude(), userPoint.longitude(),
                    marker.getLat(), marker.getLng(),
                    results
            );

            if (results[0] <= PROXIMITY_RADIUS_METERS) {
                String key = marker.id != null ? marker.id : marker.getName();
                Long last = lastNotifiedAt.get(key);
                if (last == null || (now - last) > NOTIFY_COOLDOWN_MS) {
                    sendProximityNotification(marker, Math.round(results[0]));
                    lastNotifiedAt.put(key, now);
                }
            }
        }
    }

    private void updateGeofences() {
        if (!hasLocationPermissions()) return;

        List<Geofence> geofences = new ArrayList<>();
        for (Marker marker : allMarkers) {
            if (marker == null) continue;
            if (marker.getLat() == 0.0 && marker.getLng() == 0.0) continue;
            String requestId = marker.id != null ? marker.id : marker.getName();
            if (requestId == null) continue;

            Geofence geofence = new Geofence.Builder()
                    .setRequestId(requestId)
                    .setCircularRegion(marker.getLat(), marker.getLng(), PROXIMITY_RADIUS_METERS)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .build();
            geofences.add(geofence);
        }

        if (geofences.isEmpty()) return;

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build();

        geofencingClient.removeGeofences(getGeofencePendingIntent())
                .addOnCompleteListener(task -> geofencingClient.addGeofences(request, getGeofencePendingIntent()));
    }

    private android.app.PendingIntent getGeofencePendingIntent() {
        if (geofencePendingIntent != null) return geofencePendingIntent;
        android.content.Intent intent = new android.content.Intent(this, MarkerGeofenceReceiver.class);
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
        }
        geofencePendingIntent = android.app.PendingIntent.getBroadcast(
                this, 0, intent, flags);
        return geofencePendingIntent;
    }

    private boolean hasLocationPermissions() {
        boolean fineGranted = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (!fineGranted) return false;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            boolean bgGranted = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            return bgGranted;
        }
        return true;
    }

    private void ensureBackgroundLocationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    2001
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001) {
            updateGeofences();
        }
    }

    private void sendProximityNotification(Marker marker, int distanceMeters) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        String title = "Nearby saved marker";
        String name = marker.getName() == null ? "Saved location" : marker.getName();
        String body = name + " is about " + distanceMeters + " m away.";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_location_on_24)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(
                (marker.id != null ? marker.id.hashCode() : name.hashCode()), builder.build());
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Marker proximity alerts",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications when you are near a saved marker");

            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private boolean isUserInRegion(Point userPoint, double centerLat, double centerLng) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                userPoint.latitude(), userPoint.longitude(),
                centerLat, centerLng,
                results
        );

        float radiusInMeters = 11000;

        return results[0] < radiusInMeters;
    }

    void showRegion(Point point) {
        mapView.getMapboxMap().setCamera(
                new CameraOptions.Builder()
                        .center(point)
                        .zoom(14.0)
                        .build()
        );
    }

    private String saveImageToInternalStorage(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            String fileName = "marker_" + System.currentTimeMillis() + ".jpg";
            File file = new File(getFilesDir(), fileName);

            OutputStream outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.close();
            inputStream.close();
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void uploadPendingImages() {
        FirebaseStorage storage = FirebaseStorage.getInstance();

        for (Marker marker : allMarkers) {
            if (marker.getInternalPath() != null && marker.getImageUrl() == null) {
                File file = new File(marker.getInternalPath());
                if (!file.exists()) continue;

                Uri fileUri = Uri.fromFile(file);
                StorageReference ref = storage.getReference().child("marker_images/" + marker.id + ".jpg");

                ref.putFile(fileUri).addOnSuccessListener(taskSnapshot -> {
                    ref.getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();
                        database.collection("markers").document(marker.id)
                                .update("imageUrl", downloadUrl);

                        marker.setImageUrl(downloadUrl);
                        Log.d("Sync", "Image uploaded for: " + marker.getName());
                    });
                });
            }
        }
    }
}
