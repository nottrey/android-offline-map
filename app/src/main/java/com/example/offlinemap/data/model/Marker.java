package com.example.offlinemap.data.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.GeoPoint;

public class Marker {
    private String name, mapId, uid;

    public String id;
    private String internalPath;
    private String imageUrl;
    private double lat, lng;
    private String category, description;

    public Marker(){
    }

    public Marker(String id, String name, String mapId, String uid, double lat, double lng){
        this.name = name;
        this.id = id;
        this.mapId=mapId;
        this.uid = uid;
        this.lat=lat;
        this.lng=lng;

    }
    public String getDescription(){
        return description;
    }
    public String getCategory(){
        return category;
    }

    public void setImageUrl(String url){
        this.imageUrl = url;
    }

    public String getImageUrl(){
        return imageUrl;
    }

    public void setUid(String uid){
        this.uid = uid;
    }

    public void setInternalPath(String internalPath){
        this.internalPath = internalPath;
    }

    public String getInternalPath(){
        return internalPath;
    }

    public void setDescription(String description){
        this.description = description;
    }

    public void setCategory(String category){
        this.category = category;
    }

    public String getName(){
        return name;
    }
    public void setName(String name){
        this.name=name;
    }
    public String getId(){return id;}

    public String getMapId(){
        return mapId;
    }

    public double getLat(){
        return lat;
    }

    public void setLat(double lat){
        this.lat = lat;
    }

    public void setLng(double lng){
        this.lng = lng;
    }

    public double getLng(){
        return lng;
    }

    public String getUid() {
        return uid;
    }




}
