package com.example.offlinemap;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinemap.data.model.Area;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.geojson.Point;
import com.mapbox.search.ApiType;
import com.mapbox.search.ResponseInfo;
import com.mapbox.search.SearchCallback;
import com.mapbox.search.SearchEngine;
import com.mapbox.search.SearchEngineSettings;
import com.mapbox.search.SearchOptions;
import com.mapbox.search.ServiceProvider;
import com.mapbox.search.offline.OfflineResponseInfo;
import com.mapbox.search.offline.OfflineSearchEngine;
import com.mapbox.search.offline.OfflineSearchEngineSettings;
import com.mapbox.search.offline.OfflineSearchResult;
import com.mapbox.search.record.HistoryDataProvider;
import com.mapbox.search.record.HistoryRecord;
import com.mapbox.search.result.SearchResult;
import com.mapbox.search.result.SearchSuggestion;
import com.mapbox.search.ui.adapter.engines.SearchEngineUiAdapter;
import com.mapbox.search.ui.view.CommonSearchViewConfiguration;
import com.mapbox.search.ui.view.DistanceUnitType;
import com.mapbox.search.ui.view.SearchResultsView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DisplayAllMaps extends AppCompatActivity implements TextWatcher {

    RecyclerView recyclerView;
    ArrayList<Area> areas;

    EditText searchMap;
    SearchResultsView searchResultsView;
    AreaAdapter adapter;
    OfflineSearchEngine offlineSearchEngine;

    SearchEngine searchEngine;
    SearchEngineUiAdapter searchEngineUiAdapter;

    mapHandler handler;

    ArrayList<Area>allAreasList;

    private FirebaseFirestore database;
    private Button logoutBtn;

        @Override
        protected void onCreate(Bundle savedInstanceState) {

            super.onCreate(savedInstanceState);
            setContentView(R.layout.maps_list);

            searchMap = findViewById(R.id.searchEt);
            searchResultsView = findViewById(R.id.search_results_view);
            logoutBtn = findViewById(R.id.btnLogout);

            searchResultsView.initialize(new SearchResultsView.Configuration(new CommonSearchViewConfiguration(DistanceUnitType.IMPERIAL)));
            allAreasList = new ArrayList<>();

            String accessToken = getString(R.string.mapbox_access_token);
            offlineSearchEngine = OfflineSearchEngine.create(new OfflineSearchEngineSettings(accessToken));
            LocationEngine locationEngine = LocationEngineProvider.getBestLocationEngine(this);
            HistoryDataProvider historyDataProvider = ServiceProvider.getInstance().historyDataProvider();

            searchEngine = SearchEngine.createSearchEngineWithBuiltInDataProviders(ApiType.GEOCODING, new SearchEngineSettings(accessToken));
            searchEngineUiAdapter = new SearchEngineUiAdapter(searchResultsView, searchEngine, offlineSearchEngine, locationEngine, historyDataProvider);

            searchEngineUiAdapter.addSearchListener(new SearchEngineUiAdapter.SearchListener() {

                @Override
                public void onSuggestionsShown(@NonNull List<SearchSuggestion> list, @NonNull ResponseInfo responseInfo) {

                }

                @Override
                public void onCategoryResultsShown(@NonNull SearchSuggestion searchSuggestion, @NonNull List<SearchResult> list, @NonNull ResponseInfo responseInfo) {

                }


                @Override
                public void onOfflineSearchResultsShown(@NonNull List<OfflineSearchResult> results, @NonNull OfflineResponseInfo responseInfo) {
                    // Not implemented
                }

                @Override
                public boolean onSuggestionSelected(@NonNull SearchSuggestion searchSuggestion) {
                    return false;
                }

                @Override
                public void onSearchResultSelected(@NonNull SearchResult searchResult, @NonNull ResponseInfo responseInfo) {
                    String selectedName = searchResult.getName();

                    int existingPosition = -1;
                    for (int i = 0; i < allAreasList.size(); i++) {
                        if (allAreasList.get(i).name.equalsIgnoreCase(selectedName)) {
                            existingPosition = i;
                            break;
                        }
                    }

                    if (existingPosition != -1) {

                        recyclerView.smoothScrollToPosition(existingPosition);

                    } else {

                        com.mapbox.geojson.Point point = searchResult.getCoordinate();
                        String uniqueId = database.collection("regions").document().getId();

                        Area newArea = new Area();
                        newArea.setId(uniqueId);
                        newArea.name = selectedName;
                        newArea.setDownloaded(false);
                        if (searchResult.getAddress() != null) {
                            String country = searchResult.getAddress().getCountry();
                            newArea.setCountry(country);
                        }
                        Point centerPoint = searchResult.getCoordinate();
                        if (point != null) {
                            com.mapbox.geojson.Point center = searchResult.getCoordinate();
                            double lat = center.latitude();
                            double lng = center.longitude();

                            double offset = 0.1;

                            double north = lat + offset;
                            double south = lat - offset;
                            double east = lng + offset;
                            double west = lng - offset;

                            newArea.setNorth(north);
                            newArea.setSouth(south);
                            newArea.setEast(east);
                            newArea.setWest(west);
                            newArea.setLat(lat);
                            newArea.setLng(lng);
                            newArea.setLat(centerPoint.latitude());
                            newArea.setLng(centerPoint.longitude());
                        }

                        allAreasList.add(newArea);
                        areas.add(newArea);

                        saveAreaToFirestore(newArea);


                        adapter.notifyItemInserted(areas.size() - 1);
                        recyclerView.smoothScrollToPosition(areas.size() - 1);
                    }

                    // Common cleanup
                    searchMap.setText("");
                    searchResultsView.setVisibility(View.GONE);
                    hideKeyboard();

                }

                @Override
                public void onOfflineSearchResultSelected(@NonNull OfflineSearchResult searchResult, @NonNull OfflineResponseInfo responseInfo) {
                }

                @Override
                public void onError(@NonNull Exception e) {
                }

                @Override
                public void onHistoryItemClick(@NonNull HistoryRecord historyRecord) {
                }

                @Override
                public void onPopulateQueryClick(@NonNull SearchSuggestion suggestion, @NonNull ResponseInfo responseInfo) {
                    searchMap.setText(suggestion.getName());
                }

                @Override
                public void onFeedbackItemClick(@NonNull ResponseInfo responseInfo) {
                    // Not implemented
                }
            });


            areas = new ArrayList<>();
            handler = new mapHandler(this);





            searchMap.addTextChangedListener(this);

            database = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build();
            database.setFirestoreSettings(settings);

            recyclerView = findViewById(R.id.rv);
            if (recyclerView != null) {
                recyclerView.setLayoutManager(new LinearLayoutManager(this));
                adapter = new AreaAdapter(this, areas);
                recyclerView.setAdapter(adapter);
            } else {
                Log.e("DisplayAllMaps-test", "RecyclerView not found!");
            }

            if (logoutBtn != null) {
                logoutBtn.setOnClickListener(v -> logout());
            }

        }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        getSharedPreferences("user_session", MODE_PRIVATE).edit().clear().apply();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }



    private void saveAreaToFirestore(Area area){

            String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
            if (uid == null) return;

            area.setUid(uid);

            database.collection("regions")
                    .document(area.getId())
                    .set(area)
                    .addOnSuccessListener(aVoid -> Log.d("Firestore", "Region saved to global collection"))
                    .addOnFailureListener(e -> Log.e("Firestore", "Error: " + e.getMessage()));
        }




        @Override
        protected void onResume() {
            super.onResume();
            Log.d("DisplayAllMaps-test", "sync");
            syncOfflineStatus();

        }


    protected void syncOfflineStatus() {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            return;
        }

        handler.getDownloadedRegionIds(new mapHandler.OnIdsLoadedListener() {
            @Override
            public void onLoaded(ArrayList<String> downloadedIds) {

                database.collection("regions")
                        .whereEqualTo("uid", uid)
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            ArrayList<Area> mergedList = new ArrayList<>();

                            mergedList.add(createCurrentLocationArea());

                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                Area area = doc.toObject(Area.class);

                                if (area != null) {
                                    boolean existsOnThisDevice = (downloadedIds != null && downloadedIds.contains(area.getId()));
                                    area.setDownloaded(existsOnThisDevice);
                                    mergedList.add(area);
                                }
                            }

                            runOnUiThread(() -> {
                                allAreasList.clear();
                                allAreasList.addAll(mergedList);

                                areas.clear();
                                areas.addAll(mergedList);
                                adapter.notifyDataSetChanged();
                            });
                        })
                        .addOnFailureListener(e -> {
                            Log.e("SyncDebug", "Firestore fetch failed, showing local only", e);
                            displayOnlyLocalMaps(downloadedIds);
                        });
            }

            @Override
            public void onError(String error) {
                Log.e("SyncDebug", "Local storage check failed: " + error);
            }
        });
    }


    private void displayOnlyLocalMaps(ArrayList<String> downloadedIds) {
        ArrayList<Area> localList = new ArrayList<>();
        localList.add(createCurrentLocationArea());

        for (String id : downloadedIds) {
            Area a = new Area();
            ArrayList<String> info = handler.getNameFromJson(id);
            a.setId(id);
            a.name = info.get(0);
            a.setCountry(info.get(1));
            a.setDownloaded(true);
            localList.add(a);
        }

        runOnUiThread(() -> {
            areas.clear();
            areas.addAll(localList);
            adapter.notifyDataSetChanged();
        });
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        if (charSequence.length() > 0) {
            searchResultsView.setVisibility(View.VISIBLE);
            searchEngineUiAdapter.search(charSequence.toString());
        } else {
            searchResultsView.setVisibility(View.GONE);
        }


    }


    public void updateList(ArrayList<Area> newList) {
        this.areas.clear();
        this.areas.addAll(newList);
        adapter.notifyDataSetChanged();
    }

    private Area createCurrentLocationArea() {
        Area currentLoc = new Area();
        currentLoc.setId("current_location");
        currentLoc.name = "Current Location";
        currentLoc.country = "";
        currentLoc.setLng(0.0);
        currentLoc.setLat(0.0);
        currentLoc.setDownloaded(false);
        return currentLoc;
    }


    @Override
    public void afterTextChanged(Editable editable) {

    }


}
