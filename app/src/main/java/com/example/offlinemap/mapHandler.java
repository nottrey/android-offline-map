package com.example.offlinemap;


import android.app.AlertDialog;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.example.offlinemap.data.model.Area;
import com.example.offlinemap.data.model.Marker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.gson.Gson;
import com.mapbox.common.TileRegionLoadOptions;
import com.mapbox.common.TileStore;
import com.mapbox.common.TilesetDescriptor;
import com.mapbox.geojson.Geometry;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.OfflineManager;
import com.mapbox.maps.ResourceOptions;
import com.mapbox.maps.Style;
import com.mapbox.maps.TilesetDescriptorOptions;
import com.mapbox.common.TileRegion;
import com.mapbox.maps.plugin.LocationPuck2D;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentUtils;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class mapHandler {

     private final TileStore tileStore;
     Context context;


    public mapHandler(Context context){
        this.context = context;
        this.tileStore = TileStore.create(context.getFilesDir().getAbsolutePath());


    }
    public interface OnIdsLoadedListener {
        void onLoaded(ArrayList<String>ids);
        void onError(String error);
    }



    public void getDownloadedRegionIds(OnIdsLoadedListener listener) {
        String currentUid = FirebaseAuth.getInstance().getUid();

        tileStore.getAllTileRegions(result -> {
            if (result.isValue()) {
                List<TileRegion> regions = result.getValue();
                ArrayList<String> filteredIds = new ArrayList<>();

                List<String> userOwnedIds = getIdsFromNamesJsonForUser(currentUid);

                if (regions != null) {
                    for (TileRegion r : regions) {
                        if (userOwnedIds.contains(r.getId())) {
                            filteredIds.add(r.getId());
                        }
                    }
                }
                listener.onLoaded(filteredIds);
            } else {
                listener.onError("Storage unreachable");
            }
        });
    }

    private List<String> getIdsFromNamesJsonForUser(String uid) {
        List<String> userOwnedIds = new ArrayList<>();
        File file = new File(context.getFilesDir(), "names.json");

        if (!file.exists()) {
            return userOwnedIds; // Return empty list
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String ownerUid = obj.optString("uid", "");

                if (ownerUid.equals(uid)) {
                    userOwnedIds.add(obj.getString("id"));
                }
            }
        } catch (Exception e) {
            Log.e("MapHandler-JSON", "Error parsing names.json for user filter", e);
        }

        return userOwnedIds;
    }


    public interface OnDownloadFinished {
        void onSuccess();
    }


    public void downloadRegion(double north, double south, double east, double west, String regionName, String id, String country, OnDownloadFinished callback) {
        Log.d("Offline-download", "start download");

        ResourceOptions resourceOptions = new ResourceOptions.Builder()
                .accessToken(context.getString(R.string.mapbox_access_token))
                .build();


        OfflineManager offlineManager = new OfflineManager(resourceOptions);

        TilesetDescriptor mapsTilesetDescriptor = offlineManager.createTilesetDescriptor(
                new TilesetDescriptorOptions.Builder()
                        .styleURI(Style.MAPBOX_STREETS)
                        .minZoom((byte) 0)
                        .maxZoom((byte) 16)
                        .build()
        );
        List<Point> points = new ArrayList<>();
        points.add(Point.fromLngLat(west, north));
        points.add(Point.fromLngLat(east, north));
        points.add(Point.fromLngLat(east, south));
        points.add(Point.fromLngLat(west, south));
        points.add(Point.fromLngLat(west, north)); // Closing point

        LineString outerLineString = LineString.fromLngLats(points);

        Polygon polygon = Polygon.fromOuterInner(outerLineString);

        TileRegionLoadOptions loadOptions = new TileRegionLoadOptions.Builder()
                .geometry(polygon)
                .descriptors(Collections.singletonList(mapsTilesetDescriptor))
                .build();


        tileStore.loadTileRegion(id, loadOptions,
                progress -> {
                    Log.d("Offline-download", "Progress: " + progress.getCompletedResourceCount());
                },
                result -> {
                    if (result.isValue()) {
                        Log.d("Offline-download", "Success");
                        if (callback != null) callback.onSuccess();
                        saveMapToJson(id, regionName, country);
                    } else {
                        Log.e("Offline-download", "Error: " + result.getError().getMessage());
                    }
                }
        );
    }


    private void saveMapToJson(String id, String name, String country){
        File file = new File(context.getFilesDir(), "names.json");
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        JSONArray jsonArray;

        try {
            if (file.exists()) {
                StringBuilder content = new StringBuilder();
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    content.append(line);
                }
                br.close();
                jsonArray = new JSONArray(content.toString());
            } else {
                jsonArray = new JSONArray();
            }

            JSONObject newMap = new JSONObject();
            newMap.put("id", id);
            newMap.put("uid", uid);
            newMap.put("name", name);
            newMap.put("country", country);


            boolean exists = false;
            for (int i = 0; i < jsonArray.length(); i++) {
                if (jsonArray.getJSONObject(i).getString("id").equals(id)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                jsonArray.put(newMap);
            }

            FileWriter writer = new FileWriter(file);
            writer.write(jsonArray.toString());
            writer.flush();
            writer.close();

            Log.d("Offline-JSON", "Saved successfully using json");

            //save corresponding markers from db to internal memort
            saveMarkersFromFirestore(id, uid);

        } catch (Exception e) {
            Log.e("Offline-JSON", "Error saving with json", e);
        }
    }

    private void saveMarkersFromFirestore(String regionId, String uid){
        FirebaseFirestore db = FirebaseFirestore.getInstance();


        db.collection("markers")
                .whereEqualTo("mapId", regionId)
                .whereEqualTo("uid", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    ArrayList<Marker> markerList = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Marker marker = doc.toObject(Marker.class);
                        marker.id = doc.getId();
                        markerList.add(marker);
                    }

                    if (!markerList.isEmpty()) {
                        saveMarkersToFile(markerList, regionId);
                        Log.d("Firestore-Sync", "Successfully synced " + markerList.size() + " markers to local storage.");
                    } else {
                        Log.d("Firestore-Sync", "No existing markers found in cloud for this region.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore-Sync", "Error fetching markers from cloud", e);
                });
    }

    public void deleteMarker(String markerId, String regionId){
        FirebaseFirestore.getInstance()
                .collection("markers")
                .document(markerId)
                .delete()
                .addOnSuccessListener(aVoid -> Log.d("Delete", "Cloud marker deleted"))
                .addOnFailureListener(e -> Log.e("Delete", "Error deleting cloud marker", e));

        ArrayList<Marker> markers = getSavedMarkersForRegion(regionId);

        boolean removed = markers.removeIf(m -> m.getId().equals(markerId));

        if (removed) {
            saveMarkersToFile(markers, regionId);
            Log.d("Delete", "Local marker deleted from region: " + regionId);
        }
    }

    public void deleteRegion(String id) {

        tileStore.removeTileRegion(id);
        removeRegionFromMasterJson(id); //delete from memory
        deleteMarkersFile(id);

        // Delete from Firestore
        FirebaseFirestore.getInstance()
                .collection("regions")
                .document(id)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d("Firestore-Delete", "Region successfully deleted from cloud!");
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore-Delete", "Error deleting region from cloud", e);
                });

        deleteMarkersFromFirestore(id); //delete markers for the map
    }

    public void updateOrAddMarkerLocal(String regionId, Marker updatedMarker) {
        ArrayList<Marker> markers = getSavedMarkersForRegion(regionId);
        boolean found = false;

        for (int i = 0; i < markers.size(); i++) {
            if (markers.get(i).getId().equals(updatedMarker.getId())) {
                markers.set(i, updatedMarker); // Update existing
                found = true;
                break;
            }
        }

        if (!found) {
            markers.add(updatedMarker); // Add new if not found
        }

        saveMarkersToFile(markers, regionId);
    }

    private void deleteMarkersFromFirestore(String regionId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();


        db.collection("markers")
                .whereEqualTo("mapId", regionId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit().addOnSuccessListener(aVoid ->
                            Log.d("Firestore-Delete", "All markers for region deleted."));
                });
    }
    private void removeRegionFromMasterJson(String id) {
        File file = new File(context.getFilesDir(), "names.json");
        if (!file.exists()) return;

        try {
            StringBuilder content = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) content.append(line);
            br.close();

            JSONArray oldArray = new JSONArray(content.toString());
            JSONArray newArray = new JSONArray();

            for (int i = 0; i < oldArray.length(); i++) {
                JSONObject obj = oldArray.getJSONObject(i);
                if (!obj.getString("id").equals(id)) {
                    newArray.put(obj);
                }
            }

            FileWriter writer = new FileWriter(file);
            writer.write(newArray.toString());
            writer.close();
        } catch (Exception e) {
            Log.e("Delete-Region", "Error updating names.json", e);
        }
    }

    public ArrayList<Marker> getSavedMarkersForRegion(String regionId) {
        File file = new File(context.getFilesDir(), "markers_" + regionId + ".json");
        if (!file.exists()) return new ArrayList<>();

        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            Marker[] markersArray = gson.fromJson(reader, Marker[].class);
            if (markersArray != null) {
                return new ArrayList<>(Arrays.asList(markersArray));
            }
        } catch (IOException e) {
            Log.e("Load-Markers", "Error reading markers file", e);
        }
        return new ArrayList<>();
    }

    private void deleteMarkersFile(String regionId) {
        File file = new File(context.getFilesDir(), "markers_" + regionId + ".json");
        if (file.exists()) {
            file.delete();
            Log.d("Delete-Markers", "Markers file deleted for " + regionId);
        }
    }

        public void loadSavedRegion(String uid, String id, MapView mapView){
            tileStore.getTileRegionGeometry(id, result -> {
                if (result.isValue() && result.getValue() != null) {
                    Geometry geometry = result.getValue();

                    Point point = calculateCenter(geometry);

                    LocationComponentPlugin locationPlugin = LocationComponentUtils.getLocationComponent(mapView);
                    locationPlugin.setLocationPuck(new LocationPuck2D());
                    locationPlugin.setEnabled(true);


                    mapView.getMapboxMap().setCamera(
                            new CameraOptions.Builder()
                                    .center(point)
                                    .zoom(14.0)
                                    .build()
                    );

                    Log.d("Offline-test", "Offline map successfully loaded");


                } else {
                    Log.e("Mapbox", "Could not find geometry for ID: " + id);
                }
            });
        }

    public void saveMarker(String regionId, Marker newMarker) {

        ArrayList<Marker> existingMarkers = getSavedMarkersForRegion(regionId);
        existingMarkers.add(newMarker);
        File file = new File(context.getFilesDir(), "markers_" + regionId + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new Gson();
            writer.write(gson.toJson(existingMarkers));
            Log.d("Save-Marker", "Single marker added. Total: " + existingMarkers.size());
        } catch (IOException e) {
            Log.e("Save-Marker", "Failed to save marker", e);
        }
    }

    public void saveMarkersToFile(ArrayList<Marker> list, String regionId) {

        File file = new File(context.getFilesDir(), "markers_" + regionId + ".json");

        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new Gson();
            gson.toJson(list, writer);

            Log.d("MapHandler-IO", "Successfully saved " + list.size() + " markers for region: " + regionId);
        } catch (IOException e) {
            Log.e("MapHandler-IO", "Error writing markers to disk", e);
            throw new RuntimeException("Failed to save markers locally", e);
        }
    }


    public ArrayList<String> getNameFromJson(String id) {
        File file = new File(context.getFilesDir(), "names.json");
        ArrayList<String> res = new ArrayList<>();

        String defaultName = "Saved Map: " + id;
        String defaultCountry = "Offline Area";

        if (!file.exists()) {
            res.add(defaultName);
            res.add("");
            return res;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);

                if (obj.has("id") && obj.getString("id").equals(id)) {
                    String name = obj.optString("name", defaultName);
                    String country = obj.optString("country", "");

                    res.add(name);
                    res.add(country);
                    return res;
                }
            }
        } catch (Exception e) {
            Log.e("JSON_READ", "Error reading names.json", e);
        }

        res.add(defaultName);
        res.add(defaultCountry);
        return res;
    }

    private Point calculateCenter(Geometry geometry) {
        if (geometry instanceof Point) {
            return (Point) geometry;
        }

        if (geometry instanceof Polygon) {
            List<List<Point>> coordinates = ((Polygon) geometry).coordinates();
            if (!coordinates.isEmpty() && !coordinates.get(0).isEmpty()) {
                List<Point> outerRing = coordinates.get(0);

                double minLng = Double.MAX_VALUE;
                double maxLng = -Double.MAX_VALUE;
                double minLat = Double.MAX_VALUE;
                double maxLat = -Double.MAX_VALUE;

                for (Point p : outerRing) {
                    if (p.longitude() < minLng) minLng = p.longitude();
                    if (p.longitude() > maxLng) maxLng = p.longitude();
                    if (p.latitude() < minLat) minLat = p.latitude();
                    if (p.latitude() > maxLat) maxLat = p.latitude();
                }


                return Point.fromLngLat((minLng + maxLng) / 2, (minLat + maxLat) / 2);
            }
        }

        return Point.fromLngLat(0.0, 0.0);
    }

}
