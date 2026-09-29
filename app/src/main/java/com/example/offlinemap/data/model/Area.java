package com.example.offlinemap.data.model;

import android.view.MotionEvent;
import android.view.View;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.GeoPoint;

public class Area {
    public String name;
    public String country;
     double lat;
     double lng;
     boolean downloaded;

     double north, south, east, west;

    @DocumentId
    public String id;
    public String uid;

    public Area(){

    }
    public Area(String name){
        this.name = name;
    }
    public double getLat() { return lat; }

    public String getUid() {return uid;}
    public void setUid(String uid){
        this.uid = uid;
    }
    public double getLng() { return lng; }

    public void setId(String id){
        this.id = id;
    }

    public double getNorth(){
        return north;
    }

    public double getSouth(){
        return south;
    }

    public double getEast(){
        return east;
    }

    public double getWest(){
        return west;
    }

    public void setNorth(double north){
        this.north = north;
    }

    public void setSouth(double south){
        this.south = south;
    }

    public void setEast(double east){
        this.east = east;
    }

    public void setWest(double west){
        this.west = west;
    }


    public void setDownloaded(boolean ok){
        downloaded = ok;
    }

    public boolean isDownloaded(){
        return downloaded;
    }

    @Exclude
    public String getId(){
        return id;
    }

    public void setLat(double lat){
        this.lat = lat;
    }

    public String getCountry(){
        return country;
    }

    public void setCountry(String country){
        this.country = country;
    }

    public void setLng(double lng){
        this.lng = lng;
    }

}
