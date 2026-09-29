package com.example.offlinemap;

import android.net.Uri;

public class User {

    public String uid;//user uid

    public String username;
    public String email;


    public User(String name, String email) {
        this.username = name;
        this.email = email;
    }

    public User(String name, String email, String uid) {
        this.username = name;

        this.uid = uid;
        this.email = email;
    }

    public User() {
    }


    public void setUid(String uid){
        this.uid = uid;
    }


    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername(){
        return username;
    }
    public String getEmail(){
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

