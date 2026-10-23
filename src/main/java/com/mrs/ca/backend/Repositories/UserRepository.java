package com.mrs.ca.backend.Repositories;

import com.mrs.ca.backend.Models.ClientType;
import com.mrs.ca.backend.Models.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUserId(String userId);

    Optional<User> findByEmail(String email);

    List<User> findByActive(boolean active);

    List<User> findByCreatedByAdmin(String adminId);

    boolean existsByUserId(String userId);

    boolean existsByEmail(String email);

    List<User> findByActiveTrueAndFormationDateIsNotNull();

    List<User> findByActiveTrueAndAnniversaryAutomationEnabledTrueAndFormationDateIsNotNull();

    List<User> findByClientType(ClientType clientType);

    List<User> findByActiveTrueAndClientType(ClientType clientType);

    List<User> findByActiveTrueAndClientTypeAndFormationDateIsNotNull(ClientType clientType);

    List<User> findByClientTypeIsNull();

    List<User> findByActiveTrueAndDateOfBirthIsNotNull();

    List<User> findByClientTypeAndActiveTrueAndDateOfBirthIsNotNull(ClientType clientType);

    List<User> findByClientTypeAndBirthdayMonthAndBirthdayDayAndActiveTrue(ClientType clientType, int birthdayMonth, int birthdayDay);

    void deleteByUserId(String userId);
}
