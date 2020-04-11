package com.objects;

import java.io.Serializable;

public class Session implements Serializable {

    private final String hash;
    private String clientID;

    public Session(String hash, String clientID){
        this.clientID = clientID;
        this.hash = hash;
    }

    public String getHash(){
        return hash;
    }

    public String getClientID(){ return clientID; }
}
