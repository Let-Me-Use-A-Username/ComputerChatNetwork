package com.objects;

public class Credentials {

    private String clientID;
    private String password;

    public Credentials(String clientID, String password) {
        this.clientID = clientID;
        this.password = password;
    }

    public String getClientID() {
        return clientID;
    }

    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
