package com.objects;

public class Session {

    private final String hash;

    public Session(String hash){
        this.hash = hash;
    }

    public String getHash(){
        return hash;
    }
}
