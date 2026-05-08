package org.controller;

import org.dto.PreferencesRequest;
import org.entity.User;
import org.entity.UserPreferences;
import org.repository.UserPreferencesRepository;
import org.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/preferences")
public class PreferencesController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPreferencesRepository preferencesRepository;

    @PostMapping
    public ResponseEntity<?> upsert(@RequestBody PreferencesRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing email"));
            }
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            UserPreferences pref = preferencesRepository.findByUser(user).orElse(new UserPreferences());
            pref.setUser(user);
            pref.setTone((request.getTone() == null || request.getTone().isBlank()) ? "DEFAULT" : request.getTone().toUpperCase());

            return ResponseEntity.ok(preferencesRepository.save(pref));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save preferences: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> get(@RequestParam("email") String email) {
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return ResponseEntity.ok(preferencesRepository.findByUser(user).orElse(null));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch preferences: " + e.getMessage()));
        }
    }
}

