package org.controller;

import org.dto.PreferencesRequest;
import org.entity.User;
import org.entity.UserPreferences;
import org.exception.BadRequestException;
import org.exception.ResourceNotFoundException;
import org.repository.UserPreferencesRepository;
import org.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/preferences")
public class PreferencesController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPreferencesRepository preferencesRepository;

    @PostMapping
    public ResponseEntity<UserPreferences> upsert(@RequestBody PreferencesRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException("Missing email");
        }
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserPreferences pref = preferencesRepository.findByUser(user).orElse(new UserPreferences());
        pref.setUser(user);
        pref.setTone((request.getTone() == null || request.getTone().isBlank()) ? "DEFAULT" : request.getTone().toUpperCase());

        return ResponseEntity.ok(preferencesRepository.save(pref));
    }

    @GetMapping
    public ResponseEntity<UserPreferences> get(@RequestParam("email") String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(preferencesRepository.findByUser(user).orElse(null));
    }
}

