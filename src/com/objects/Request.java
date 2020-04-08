package com.objects;

public class Request {

    private String tag;
    private Session cookie;
    private RequestType request;
    private String bodyType;
    private Object body;


    public Request(String tag, Session cookie, RequestType request, String bodyType, Object body) {
        this.tag = tag;
        this.cookie = cookie;
        this.request = request;
        this.bodyType = bodyType;
        this.body = body;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Session getCookie() {
        return cookie;
    }

    public void setCookie(Session cookie) {
        this.cookie = cookie;
    }

    public RequestType getRequest() {
        return request;
    }

    public void setRequest(RequestType request) {
        this.request = request;
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
}
