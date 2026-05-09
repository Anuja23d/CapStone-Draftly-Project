package org.controller;

import org.dto.DraftGenerateForEmailRequest;
import org.dto.DraftRequest;
import org.dto.DraftSendRequest;
import org.dto.DraftUpdateRequest;
import org.entity.Draft;
import org.exception.BadRequestException;
import org.service.DraftService;
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
    private DraftService draftService;

    @GetMapping
    public List<Draft> getAllDrafts() {
        return draftService.getAllDrafts();
    }

    @GetMapping("/status/{status}")
    public List<Draft> getByStatus(@PathVariable("status") String status) {
        return draftService.getDraftsByStatus(status);
    }

    @PostMapping("/draft/generate")
    public ResponseEntity<Map<String, String>> generate(
            @RequestBody DraftRequest request,
            @AuthenticationPrincipal OAuth2User principal
    ) throws Exception {
        String email = (principal != null) ? principal.getAttribute("email") : request.getEmail();
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Missing user email. Login via /oauth2/authorization/google or send {\"email\": \"you@gmail.com\"}.");
        }
        String reply = draftService.generateDraft(
                request.getBody(),
                email
        ).getGeneratedText();

        return ResponseEntity.ok(Map.of("generatedText", reply));
    }

    @PostMapping("/draft/generate-for-email")
    public ResponseEntity<Draft> generateForEmail(@RequestBody DraftGenerateForEmailRequest request) throws Exception {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException("Missing email");
        }
        if (request.getEmailId() == null) {
            throw new BadRequestException("Missing emailId");
        }
        return ResponseEntity.ok(draftService.generateDraftForEmail(request.getEmailId(), request.getEmail()));
    }

    @PatchMapping("/{draftId}")
    public ResponseEntity<Draft> editDraft(@PathVariable("draftId") Long draftId, @RequestBody DraftUpdateRequest request) {
        return ResponseEntity.ok(draftService.editDraft(draftId, request.getEditedText()));
    }

    @PostMapping("/{draftId}/approve")
    public ResponseEntity<Draft> approve(@PathVariable("draftId") Long draftId) {
        return ResponseEntity.ok(draftService.approveDraft(draftId));
    }

    @PostMapping("/{draftId}/reject")
    public ResponseEntity<Draft> reject(@PathVariable("draftId") Long draftId) {
        return ResponseEntity.ok(draftService.rejectDraft(draftId));
    }

    @PostMapping("/{draftId}/send")
    public ResponseEntity<Draft> send(@PathVariable("draftId") Long draftId, @RequestBody DraftSendRequest request) throws Exception {
        return ResponseEntity.ok(draftService.sendApprovedDraft(
                draftId,
                request.getEmail(),
                request.getIdempotencyKey()
        ));
    }


}
