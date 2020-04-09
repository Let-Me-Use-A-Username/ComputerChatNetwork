package com.objects;

import java.io.Serializable;

public class Response implements Serializable {

    private String tag;
    private ResponseType response;
    private String bodyType;
    private Object body;
    private Session session;

    public Response(String tag, ResponseType response, String bodyType, Object body) {
        this.tag = tag;
        this.response = response;
        this.bodyType = bodyType;
        this.body = body;
    }

    public Response(String tag, ResponseType response, String bodyType, Object body, Session session) {
        this.tag = tag;
        this.response = response;
        this.bodyType = bodyType;
        this.body = body;
        this.session = session;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public ResponseType getResponse() {
        return response;
    }

    public void setResponse(ResponseType response) {
        this.response = response;
    }

    public String getBodyType() {
        return bodyType;
    }

    public void setBodyType(String bodyType) {
        this.bodyType = bodyType;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public Session getSession() { return session; }

    public void setSession(Session session) { this.session = session; }

}
