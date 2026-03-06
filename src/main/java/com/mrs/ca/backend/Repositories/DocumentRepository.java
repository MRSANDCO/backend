package com.mrs.ca.backend.Repositories;

import com.mrs.ca.backend.Models.Document;
import com.mrs.ca.backend.Models.DocumentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends MongoRepository<Document, String> {

    List<Document> findByOwnerUserId(String userId);

    List<Document> findByUploadedByAdmin(String adminId);

    List<Document> findByStatus(DocumentStatus status);

    List<Document> findByCategory(String category);

    List<Document> findByOwnerUserIdAndStatus(String userId, DocumentStatus status);

    List<Document> findByTitleContainingIgnoreCase(String keyword);
}
