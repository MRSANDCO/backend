package com.mrs.ca.backend.dto;

public class QueryResponseRequest {

    private String message;

    public QueryResponseRequest() {}

    public QueryResponseRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
