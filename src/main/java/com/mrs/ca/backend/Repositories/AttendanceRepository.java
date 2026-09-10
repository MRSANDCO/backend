package com.mrs.ca.backend.Repositories;

import com.mrs.ca.backend.Models.Attendance;
import com.mrs.ca.backend.Models.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends MongoRepository<Attendance, String> {

    Optional<Attendance> findByAttendanceId(String attendanceId);

    Optional<Attendance> findByEmployeeIdAndAttendanceDate(String employeeId, LocalDate attendanceDate);

    List<Attendance> findByEmployeeIdOrderByAttendanceDateDesc(String employeeId);

    Page<Attendance> findByEmployeeIdOrderByAttendanceDateDesc(String employeeId, Pageable pageable);

    List<Attendance> findByAttendanceDate(LocalDate attendanceDate);

    List<Attendance> findByAttendanceDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("{ '$and': [ " +
            "  { '$or': [ { '?0': null }, { 'employee_id': ?0 } ] }, " +
            "  { '$or': [ { '?1': null }, { 'attendance_date': ?1 } ] }, " +
            "  { '$or': [ { '?2': null }, { 'status': ?2 } ] }, " +
            "  { '$or': [ { '?3': null }, { 'attendance_date': { '$gte': ?3 } } ] }, " +
            "  { '$or': [ { '?4': null }, { 'attendance_date': { '$lte': ?4 } } ] } " +
            "] }")
    Page<Attendance> findFilteredAttendance(
            String employeeId,
            LocalDate attendanceDate,
            AttendanceStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    );
}
