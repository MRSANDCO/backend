package com.mrs.ca.backend.Repositories;

import com.mrs.ca.backend.Models.QueryResponse;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueryResponseRepository extends MongoRepository<QueryResponse, String> {

    /** Find all responses for a query, ordered chronologically (oldest first). */
    List<QueryResponse> findByQueryIdOrderByCreatedAtAsc(String queryId);

    /** Find all responses for a query, ordered newest first. */
    List<QueryResponse> findByQueryIdOrderByCreatedAtDesc(String queryId);

    /** Delete all responses associated with a query. */
    void deleteByQueryId(String queryId);
}
