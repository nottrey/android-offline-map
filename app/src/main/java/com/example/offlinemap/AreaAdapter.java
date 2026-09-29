package com.example.offlinemap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinemap.data.model.Area;
import com.example.offlinemap.ui.MapViewModel;

import java.util.ArrayList;

public class AreaAdapter extends RecyclerView.Adapter<AreaAdapter.PlaceViewHolder> {

    Context context;
    ArrayList<Area> objects;


    public AreaAdapter(Context context, ArrayList<Area> places){
        this.context=context;
        this.objects=places;

    }


    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.map_card, parent, false);
        return new PlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AreaAdapter.PlaceViewHolder holder, int position) {
        Area area = objects.get(position);
        // Bind data to views
        holder.areaTv.setText(area.name);
        String region = area.country == null ? "" : area.country;
        String regionMain = region;
        String regionSub = "";
        if (region.contains(",")) {
            String[] parts = region.split(",");
            if (parts.length > 0) {
                regionMain = parts[0].trim();
            }
            if (parts.length > 1) {
                regionSub = parts[1].trim();
            }
        }
        holder.countryTv.setText(regionMain);
        String regionCode = !regionSub.isEmpty() ? regionSub : regionMain;
        holder.regionCodeTv.setText(regionCode.toUpperCase());
        if (area.getId().equals("current_location")) {
            holder.downloadBtn.setVisibility(View.GONE); // Hide download
            holder.deleteBtn.setVisibility(View.GONE);
            holder.savedPill.setVisibility(View.GONE);
            holder.metaTv.setText("Current location");
            holder.statusDot.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.ui_muted)
                    )
            );
        } else if (area.isDownloaded()) {
            holder.downloadBtn.setVisibility(View.GONE);
            holder.downloadBtn.setEnabled(false);
            holder.deleteBtn.setVisibility(View.VISIBLE);
            holder.savedPill.setVisibility(View.VISIBLE);
            holder.metaTv.setText("Saved offline");
            holder.statusDot.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.ui_accent)
                    )
            );
        } else {
            holder.downloadBtn.setVisibility(View.VISIBLE);
            holder.downloadBtn.setEnabled(true);
            holder.downloadBtn.setAlpha(1f);
            holder.deleteBtn.setVisibility(View.VISIBLE);
            holder.savedPill.setVisibility(View.GONE);
            holder.metaTv.setText("Tap to save");
            holder.statusDot.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.ui_muted)
                    )
            );

    }
        holder.downloadBtn.setOnClickListener(v -> {
            holder.downloadBtn.setEnabled(false);
            holder.downloadBtn.setAlpha(0.5f);
            holder.metaTv.setText("Downloading...");

            mapHandler handler = new mapHandler(v.getContext());
            handler.downloadRegion(
                    area.getNorth(), area.getSouth(), area.getEast(), area.getWest(),
                    area.name, area.getId(), area.getCountry(),
                    new mapHandler.OnDownloadFinished() {
                        @Override
                        public void onSuccess() {
                            ((Activity) context).runOnUiThread(() -> {
                                area.setDownloaded(true);
                                notifyItemChanged(holder.getAdapterPosition());
                            });
                        }


                    }
            );
        });

        holder.itemView.setOnLongClickListener(v -> {

            return true; // Returns true so the normal click isn't triggered
        });

        holder.deleteBtn.setOnClickListener(v -> {
            if (!area.getId().equals("current_location")){
                showDeleteDialog(area, holder.getAdapterPosition());
            }

        });

        holder.itemView.setOnClickListener(v -> {
            Context context = v.getContext();
            Intent intent = new Intent(context, MapViewModel.class);

            if(!area.isDownloaded()){
                intent.putExtra("name", area.name);
                intent.putExtra("lat", area.getLat());
                intent.putExtra("lng", area.getLng());
                intent.putExtra("downloaded", false);

            }

            else {
                intent.putExtra("downloaded", true);
            }
            intent.putExtra("id", area.id);

            context.startActivity(intent);

        });


    }

    private void showDeleteDialog(Area area, int position){
        new AlertDialog.Builder(context)
                .setTitle("Delete Map?")
                .setMessage("Are you sure you want to remove " + area.name + "? This will delete saved tiles and cloud markers.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    mapHandler handler = new mapHandler(context);

                    handler.deleteRegion(area.getId());

                    objects.remove(position);
                    notifyItemRemoved(position);
                    notifyItemRangeChanged(position, objects.size());

                    Toast.makeText(context, "Deleted " + area.name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }





    @Override
    public int getItemCount() {
        return objects.size();
    }



    public static class PlaceViewHolder extends RecyclerView.ViewHolder {
        TextView areaTv;
        TextView countryTv;
        TextView metaTv;
        TextView regionCodeTv;
        View statusDot;
        LinearLayout savedPill;
        ImageButton downloadBtn;

        ImageButton deleteBtn;


        public PlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            areaTv = itemView.findViewById(R.id.areaTv);
            countryTv = itemView.findViewById(R.id.regionTv);
            metaTv = itemView.findViewById(R.id.metaTv);
            regionCodeTv = itemView.findViewById(R.id.regionCodeTv);
            statusDot = itemView.findViewById(R.id.statusDot);
            savedPill = itemView.findViewById(R.id.savedPill);
            downloadBtn = itemView.findViewById(R.id.btnToDownload);
            deleteBtn = itemView.findViewById(R.id.btnDelete);
        }


    }
}
