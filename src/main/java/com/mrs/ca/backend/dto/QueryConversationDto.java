package com.mrs.ca.backend.dto;

import com.mrs.ca.backend.Models.Query;
import com.mrs.ca.backend.Models.QueryResponse;

import java.util.List;

public class QueryConversationDto {

    private Query query;
    private List<QueryResponse> responses;

    public QueryConversationDto() {}

    public QueryConversationDto(Query query, List<QueryResponse> responses) {
        this.query = query;
        this.responses = responses;
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    public List<QueryResponse> getResponses() {
        return responses;
    }

    public void setResponses(List<QueryResponse> responses) {
        this.responses = responses;
    }
}
