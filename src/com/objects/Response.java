package com.objects;

public class Response {

    private String tag;
    private ResponseType response;
    private String bodyType;
    private Object body;

    public Response(String tag, ResponseType response, String bodyType, Object body) {
        this.tag = tag;
        this.response = response;
        this.bodyType = bodyType;
        this.body = body;
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
}
