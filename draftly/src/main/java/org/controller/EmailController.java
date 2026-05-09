package org.controller;

import org.dto.ReplySendRequest;
import org.entity.Email;
import org.entity.User;
import org.exception.BadRequestException;
import org.exception.ResourceNotFoundException;
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
                                        @RequestParam(value = "unreadOnly", defaultValue = "false") boolean unreadOnly) throws Exception {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<Email> emails = unreadOnly
                ? emailService.fetchUnreadInboxEmails(user, maxResults)
                : emailService.fetchInboxEmails(user, maxResults);
        return ResponseEntity.ok(emails);
    }

    @PostMapping("/reply/send")
    public ResponseEntity<Map<String, String>> sendReply(@RequestBody ReplySendRequest request) throws Exception {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException("Missing email");
        }
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String result = emailService.sendReply(user, request.getMessageId(), request.getReplyText());
        return ResponseEntity.ok(Map.of("status", "sent", "gmailResponse", result));
    }
}
