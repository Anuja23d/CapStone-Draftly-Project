package org.controller;

import org.dto.DraftGenerateForEmailRequest;
import org.dto.DraftRequest;
import org.dto.DraftSendRequest;
import org.dto.DraftUpdateRequest;
import org.entity.Draft;
import org.repository.DraftRepository;
import org.repository.UserRepository;
import org.service.EmailService;
import org.service.DraftService;
import org.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.user.OAuth2User;

@RestController
@RequestMapping("/drafts")
public class DraftController {

    @Autowired
    private DraftRepository draftRepository;


    @Autowired
    private DraftService draftService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LogService logService;

    @GetMapping
    public List<Draft> getAllDrafts() {
        return draftRepository.findAll();
    }

    @GetMapping("/status/{status}")
    public List<Draft> getByStatus(@PathVariable("status") String status) {
        return draftRepository.findByStatus(status.toUpperCase());
    }

    @PostMapping("/draft/generate")
    public ResponseEntity<Map<String, String>> generate(
            @RequestBody DraftRequest request,
            @AuthenticationPrincipal OAuth2User principal
    ) {
try {
    String email = (principal != null) ? principal.getAttribute("email") : request.getEmail();
    if (email == null || email.isBlank()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Missing user email. Login via /oauth2/authorization/google or send {\"email\": \"you@gmail.com\"}."));
    }
    String reply = draftService.generateDraft(
            request.getBody(),
            email
    ).getGeneratedText();

    return ResponseEntity.ok(Map.of("generatedText", reply));
}catch (Exception e){
    return ResponseEntity.status(500).body(Map.of("error", "Failed to generate draft: " + e.getMessage()));
}
    }

    @PostMapping("/draft/generate-for-email")
    public ResponseEntity<?> generateForEmail(@RequestBody DraftGenerateForEmailRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing email"));
            }
            if (request.getEmailId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing emailId"));
            }
            Draft draft = draftService.generateDraftForEmail(request.getEmailId(), request.getEmail());
            return ResponseEntity.ok(draft);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to generate draft: " + e.getMessage()));
        }
    }

    @PatchMapping("/{draftId}")
    public ResponseEntity<?> editDraft(@PathVariable("draftId") Long draftId, @RequestBody DraftUpdateRequest request) {
        try {
            Draft draft = draftRepository.findById(draftId)
                    .orElseThrow(() -> new RuntimeException("Draft not found"));
            draft.setEditedText(request.getEditedText());
            draft.setStatus("PENDING");
            return ResponseEntity.ok(draftRepository.save(draft));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to edit draft: " + e.getMessage()));
        }
    }

    @PostMapping("/{draftId}/approve")
    public ResponseEntity<?> approve(@PathVariable("draftId") Long draftId) {
        try {
            Draft draft = draftRepository.findById(draftId)
                    .orElseThrow(() -> new RuntimeException("Draft not found"));
            draft.setStatus("APPROVED");
            logService.log(draftId, "APPROVED", "Draft approved");
            return ResponseEntity.ok(draftRepository.save(draft));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to approve: " + e.getMessage()));
        }
    }

    @PostMapping("/{draftId}/reject")
    public ResponseEntity<?> reject(@PathVariable("draftId") Long draftId) {
        try {
            Draft draft = draftRepository.findById(draftId)
                    .orElseThrow(() -> new RuntimeException("Draft not found"));
            draft.setStatus("REJECTED");
            logService.log(draftId, "REJECTED", "Draft rejected");
            return ResponseEntity.ok(draftRepository.save(draft));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to reject: " + e.getMessage()));
        }
    }

    @PostMapping("/{draftId}/send")
    public ResponseEntity<?> send(@PathVariable("draftId") Long draftId, @RequestBody DraftSendRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing email"));
            }
            if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing idempotencyKey"));
            }

            Draft draft = draftRepository.findById(draftId)
                    .orElseThrow(() -> new RuntimeException("Draft not found"));
            if (!"APPROVED".equalsIgnoreCase(draft.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Draft must be APPROVED before sending"));
            }

            // Idempotency: if this key already sent, return existing draft state
            var existing = draftRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                return ResponseEntity.ok(existing.get());
            }

            var user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (draft.getEmail() == null || draft.getEmail().getMessageId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Draft is not linked to an Email with messageId"));
            }

            String replyText = (draft.getEditedText() != null && !draft.getEditedText().isBlank())
                    ? draft.getEditedText()
                    : draft.getGeneratedText();

            draft.setIdempotencyKey(request.getIdempotencyKey());
            draftRepository.save(draft);

            logService.log(draftId, "SEND_ATTEMPT", "Attempting send");

            // basic retry loop (network/transient)
            Exception last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    String gmailResponse = emailService.sendReply(user, draft.getEmail().getMessageId(), replyText);
                    draft.setStatus("SENT");
                    draft.setSentMessageId(gmailResponse);
                    logService.log(draftId, "SENT", "Sent via Gmail API");
                    return ResponseEntity.ok(draftRepository.save(draft));
                } catch (Exception e) {
                    last = e;
                    logService.log(draftId, "SEND_RETRY_" + attempt, e.getMessage());
                }
            }

            draft.setStatus("FAILED");
            draftRepository.save(draft);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send after retries: " + (last == null ? "" : last.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send: " + e.getMessage()));
        }
    }


}