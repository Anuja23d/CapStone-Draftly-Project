package org.controller;

import org.dto.ReplySendRequest;
import org.entity.Email;
import org.entity.User;
import org.repository.UserRepository;
import org.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/emails")
public class EmailController {

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/inbox")
    public ResponseEntity<?> fetchInbox(@RequestParam("email") String email,
                                        @RequestParam(value = "maxResults", defaultValue = "10") int maxResults,
                                        @RequestParam(value = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            List<Email> emails = unreadOnly
                    ? emailService.fetchUnreadInboxEmails(user, maxResults)
                    : emailService.fetchInboxEmails(user, maxResults);
            return ResponseEntity.ok(emails);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch inbox: " + e.getMessage()));
        }
    }

    @PostMapping("/reply/send")
    public ResponseEntity<Map<String, String>> sendReply(@RequestBody ReplySendRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing email"));
            }
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String result = emailService.sendReply(user, request.getMessageId(), request.getReplyText());
            return ResponseEntity.ok(Map.of("status", "sent", "gmailResponse", result));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send reply: " + e.getMessage()));
        }
    }
}