package com.objects;

import java.io.Serializable;

public class Session implements Serializable {

    private final String hash;

    public Session(String hash){
        this.hash = hash;
    }

    public String getHash(){
        return hash;
    }
}
