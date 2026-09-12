package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.JwtUtil;
import com.mrs.ca.backend.Models.Employee;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.AdminService;
import com.mrs.ca.backend.Services.EmployeeService;
import com.mrs.ca.backend.Services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminService adminService;
    private final UserService userService;
    private final EmployeeService employeeService;
    private final JwtUtil jwtUtil;

    public AuthController(AdminService adminService, UserService userService,
                          EmployeeService employeeService, JwtUtil jwtUtil) {
        this.adminService = adminService;
        this.userService = userService;
        this.employeeService = employeeService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "username and password are required"));
        }

        if (adminService.authenticateAdmin(username, password)) {
            String token = jwtUtil.generateToken("admin", "admin");
            return ResponseEntity.ok(Map.of(
                    "message", "Login successful",
                    "role", "admin",
                    "token", token));
        }
        return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
    }

    @PostMapping("/user/login")
    public ResponseEntity<?> userLogin(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String password = request.get("password");

        if (userId == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "userId and password are required"));
        }

        Optional<User> user = userService.authenticateUser(userId, password);
        if (user.isPresent()) {
            String token = jwtUtil.generateToken(user.get().getUserId(), "user");
            return ResponseEntity.ok(Map.of(
                    "message", "Login successful",
                    "role", "user",
                    "userId", user.get().getUserId(),
                    "fullName", user.get().getFullName() != null ? user.get().getFullName() : "",
                    "token", token));
        }
        return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
    }

    @PostMapping("/employee/login")
    public ResponseEntity<?> employeeLogin(@RequestBody Map<String, String> request) {
        String employeeId = request.get("employeeId");
        if (employeeId == null || employeeId.isBlank()) {
            employeeId = request.get("userId");
        }
        String password = request.get("password");

        if (employeeId == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "employeeId and password are required"));
        }

        Optional<Employee> employee = employeeService.authenticateEmployee(employeeId.trim(), password);
        if (employee.isPresent()) {
            Employee emp = employee.get();
            boolean isCompleted = emp.getProfileStatus() == com.mrs.ca.backend.Models.ProfileStatus.SUBMITTED || Boolean.TRUE.equals(emp.getFormCompleted());
            String token = jwtUtil.generateToken(emp.getEmployeeId(), "employee");
            return ResponseEntity.ok(Map.of(
                    "message", "Login successful",
                    "role", "employee",
                    "employeeId", emp.getEmployeeId(),
                    "name", emp.getName() != null ? emp.getName() : "",
                    "profileStatus", emp.getProfileStatus().name(),
                    "formCompleted", isCompleted,
                    "isFormCompleted", isCompleted,
                    "token", token));
        }
        return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
    }
}
