package com.mrs.ca.backend.Repositories;

import com.mrs.ca.backend.Models.AssignmentStatus;
import com.mrs.ca.backend.Models.DocumentAssignment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentAssignmentRepository extends MongoRepository<DocumentAssignment, String> {

    List<DocumentAssignment> findByUserId(String userId);

    List<DocumentAssignment> findByDocumentId(String documentId);

    List<DocumentAssignment> findByUserIdAndStatus(String userId, AssignmentStatus status);

    Optional<DocumentAssignment> findByUserIdAndDocumentId(String userId, String documentId);

    List<DocumentAssignment> findByAssignedByAdmin(String adminId);

    boolean existsByUserIdAndDocumentId(String userId, String documentId);
}
