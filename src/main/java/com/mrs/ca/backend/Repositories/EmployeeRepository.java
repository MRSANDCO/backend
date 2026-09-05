package com.mrs.ca.backend.Repositories;

import com.mrs.ca.backend.Models.Employee;
import com.mrs.ca.backend.Models.ProfileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends MongoRepository<Employee, String> {

    Optional<Employee> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);

    boolean existsByMobileNumber(String mobileNumber);

    List<Employee> findByProfileStatus(ProfileStatus profileStatus);

    @Query("{ '$or': [ { 'name': { $regex: ?0, $options: 'i' } }, { 'employee_id': { $regex: ?0, $options: 'i' } }, { 'mobile_number': { $regex: ?0, $options: 'i' } } ] }")
    Page<Employee> searchEmployees(String query, Pageable pageable);
}
