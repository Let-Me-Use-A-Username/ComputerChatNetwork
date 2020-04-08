package com.network;

public class ClientNode {

    private String hostName;
    private int port;

    public ClientNode(String hostName, int port) {
        this.hostName = hostName;
        this.port = port;
    }

    public String getHostName() {
        return hostName;
    }

    public int getPort() {
        return port;
    }

    public String toString(){
        return hostName+":"+port;
    }
}
