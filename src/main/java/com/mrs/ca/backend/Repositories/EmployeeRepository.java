package com.mrs.ca.backend.Repositories;

import com.mrs.ca.backend.Models.Employee;
import com.mrs.ca.backend.Models.EmploymentStatus;
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

    void deleteByEmployeeId(String employeeId);

    // --- Employment Status queries ---

    /**
     * Paginated list of employees filtered by employment status.
     * Use ACTIVE to list current employees, EX_EMPLOYEE for ex-employees.
     */
    Page<Employee> findByEmploymentStatus(EmploymentStatus employmentStatus, Pageable pageable);

    /**
     * Count of employees by employment status — useful for dashboard totals.
     */
    long countByEmploymentStatus(EmploymentStatus employmentStatus);

    /**
     * Searches employees by name/employeeId/mobile AND filters by employment status.
     * Used when the caller specifies both a search query and a status filter.
     */
    @Query("{ '$and': [ { '$or': [ { 'name': { $regex: ?0, $options: 'i' } }, { 'employee_id': { $regex: ?0, $options: 'i' } }, { 'mobile_number': { $regex: ?0, $options: 'i' } } ] }, { 'employment_status': ?1 } ] }")
    Page<Employee> searchEmployeesByStatus(String query, EmploymentStatus employmentStatus, Pageable pageable);

    /**
     * Searches employees (by name/id/mobile) excluding any employment_status filter.
     * Same as {@link #searchEmployees} but explicitly named for clarity.
     */
    @Query("{ '$and': [ { '$or': [ { 'name': { $regex: ?0, $options: 'i' } }, { 'employee_id': { $regex: ?0, $options: 'i' } }, { 'mobile_number': { $regex: ?0, $options: 'i' } } ] }, { '$or': [ { 'employment_status': ?1 }, { 'employment_status': null } ] } ] }")
    Page<Employee> searchEmployeesByStatusOrNull(String query, EmploymentStatus employmentStatus, Pageable pageable);
}
