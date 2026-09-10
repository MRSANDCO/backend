package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.DatabaseSequence;
import com.mrs.ca.backend.Repositories.EmployeeRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class SequenceGeneratorService {

    private static final String EMPLOYEE_SEQ_KEY = "employee_sequence";
    private static final long START_SEQ = 1000L;

    private final MongoOperations mongoOperations;
    private final EmployeeRepository employeeRepository;

    public SequenceGeneratorService(MongoOperations mongoOperations, EmployeeRepository employeeRepository) {
        this.mongoOperations = mongoOperations;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Atomically generate the next Employee ID, e.g. EMP1001, EMP1002...
     */
    public synchronized String generateNextEmployeeId() {
        Query query = new Query(Criteria.where("_id").is(EMPLOYEE_SEQ_KEY));
        DatabaseSequence existing = mongoOperations.findOne(query, DatabaseSequence.class);

        if (existing == null) {
            mongoOperations.save(new DatabaseSequence(EMPLOYEE_SEQ_KEY, START_SEQ));
        }

        while (true) {
            DatabaseSequence counter = mongoOperations.findAndModify(
                    query,
                    new Update().inc("seq", 1),
                    FindAndModifyOptions.options().returnNew(true).upsert(true),
                    DatabaseSequence.class
            );

            long seq = counter != null ? counter.getSeq() : START_SEQ + 1;
            String employeeId = "EMP" + seq;

            if (!employeeRepository.existsByEmployeeId(employeeId)) {
                return employeeId;
            }
        }
    }

    /**
     * Atomically generate the next Attendance ID, e.g. ATT1001, ATT1002...
     */
    public synchronized String generateNextAttendanceId() {
        Query query = new Query(Criteria.where("_id").is("attendance_sequence"));
        DatabaseSequence counter = mongoOperations.findAndModify(
                query,
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                DatabaseSequence.class
        );
        long seq = counter != null ? counter.getSeq() : 1000L;
        return "ATT" + seq;
    }
}
