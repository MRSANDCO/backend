package com.mrs.ca.backend.Repositories;

import com.mrs.ca.backend.Models.DriveLink;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriveLinkRepository extends MongoRepository<DriveLink, String> {
    List<DriveLink> findByUserId(String userId);
    List<DriveLink> findByYear(String year);
    List<DriveLink> findByUserIdAndYear(String userId, String year);
    List<DriveLink> findAllByOrderByCreatedAtDesc();
    void deleteByUserId(String userId);
}