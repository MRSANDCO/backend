package com.mrs.ca.backend.Controllers;

import com.mrs.ca.backend.Config.JwtUtil;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Services.AdminService;
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
    private final JwtUtil jwtUtil;

    public AuthController(AdminService adminService, UserService userService, JwtUtil jwtUtil) {
        this.adminService = adminService;
        this.userService = userService;
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
}
