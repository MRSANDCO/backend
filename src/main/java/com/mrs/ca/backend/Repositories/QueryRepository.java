package com.mrs.ca.backend.Repositories;

import com.mrs.ca.backend.Models.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueryRepository extends MongoRepository<Query, String> {

    /** All queries targeting a specific user (by MongoDB _id), newest first. */
    List<Query> findByTargetUserIdOrderByCreatedAtDesc(String userId);

    /** All queries across all users, newest first. */
    List<Query> findAllByOrderByCreatedAtDesc();
}
