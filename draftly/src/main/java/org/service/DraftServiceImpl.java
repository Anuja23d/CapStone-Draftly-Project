package org.service;

import org.entity.Draft;
import org.entity.Email;
import org.entity.User;
import org.exception.BadRequestException;
import org.exception.ResourceNotFoundException;
import org.repository.DraftRepository;
import org.repository.EmailRepository;
import org.repository.UserRepository;
import org.repository.UserPreferencesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DraftServiceImpl implements DraftService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_SEND_ATTEMPTS = 3;

    @Autowired
    private AIService aiService;

    @Autowired
    private DraftRepository draftRepository;

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPreferencesRepository preferencesRepository;

    @Autowired
    private LogService logService;

    @Override
    public List<Draft> getAllDrafts() {
        return draftRepository.findAll();
    }

    @Override
    public List<Draft> getDraftsByStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("Missing status");
        }
        return draftRepository.findByStatus(status.toUpperCase());
    }

    @Override
    public Draft generateDraft(String body, String userEmail) throws Exception {

        // 🔹 Get REAL user from DB
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 🔹 Fetch sent emails for tone
        List<String> sentEmails = emailService.getSentEmailBodies(user);

        String toneContext = buildToneContext(sentEmails);
        String tone = preferencesRepository.findByUser(user).map(p -> p.getTone()).orElse("DEFAULT");

        // 🔹 Generate reply using ACTUAL email content
        String reply = aiService.generateReply(body, toneContext, tone);
        reply = normalizeAndAppendSignature(reply, user.getSignature());

        Draft draft = new Draft();
        draft.setGeneratedText(reply);
        draft.setStatus(STATUS_PENDING);

        return draftRepository.save(draft);
    }

    @Override
    public Draft generateDraftForEmail(Long emailId, String userEmail) throws Exception {
        if (emailId == null) {
            throw new BadRequestException("emailId is required");
        }
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new ResourceNotFoundException("Email not found"));
        if (email.getUser() == null || !email.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Email does not belong to user");
        }

        String body = email.getBody() == null ? "" : email.getBody();

        List<String> sentEmails = emailService.getSentEmailBodies(user);
        String toneContext = buildToneContext(sentEmails);

        String tone = preferencesRepository.findByUser(user).map(p -> p.getTone()).orElse("DEFAULT");
        String reply = aiService.generateReply(body, toneContext, tone);
        reply = normalizeAndAppendSignature(reply, user.getSignature());

        Draft draft = new Draft();
        draft.setEmail(email);
        draft.setGeneratedText(reply);
        draft.setStatus(STATUS_PENDING);
        return draftRepository.save(draft);
    }

    @Override
    public Draft editDraft(Long draftId, String editedText) {
        Draft draft = findDraft(draftId);
        draft.setEditedText(editedText);
        draft.setStatus(STATUS_PENDING);
        return draftRepository.save(draft);
    }

    @Override
    public Draft approveDraft(Long draftId) {
        Draft draft = findDraft(draftId);
        draft.setStatus(STATUS_APPROVED);
        logService.log(draftId, STATUS_APPROVED, "Draft approved");
        return draftRepository.save(draft);
    }

    @Override
    public Draft rejectDraft(Long draftId) {
        Draft draft = findDraft(draftId);
        draft.setStatus(STATUS_REJECTED);
        logService.log(draftId, STATUS_REJECTED, "Draft rejected");
        return draftRepository.save(draft);
    }

    @Override
    public Draft sendApprovedDraft(Long draftId, String email, String idempotencyKey) throws Exception {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Missing email");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Missing idempotencyKey");
        }

        Draft draft = findDraft(draftId);
        if (!STATUS_APPROVED.equalsIgnoreCase(draft.getStatus())) {
            throw new BadRequestException("Draft must be APPROVED before sending");
        }

        var existing = draftRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (draft.getEmail() == null || draft.getEmail().getMessageId() == null) {
            throw new BadRequestException("Draft is not linked to an Email with messageId");
        }

        String replyText = (draft.getEditedText() != null && !draft.getEditedText().isBlank())
                ? draft.getEditedText()
                : draft.getGeneratedText();

        draft.setIdempotencyKey(idempotencyKey);
        draftRepository.save(draft);

        logService.log(draftId, "SEND_ATTEMPT", "Attempting send");

        Exception last = null;
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                String gmailResponse = emailService.sendReply(user, draft.getEmail().getMessageId(), replyText);
                draft.setStatus(STATUS_SENT);
                draft.setSentMessageId(gmailResponse);
                logService.log(draftId, STATUS_SENT, "Sent via Gmail API");
                return draftRepository.save(draft);
            } catch (Exception e) {
                last = e;
                logService.log(draftId, "SEND_RETRY_" + attempt, e.getMessage());
            }
        }

        draft.setStatus(STATUS_FAILED);
        draftRepository.save(draft);
        throw new RuntimeException("Failed to send after retries: " + (last == null ? "" : last.getMessage()));
    }

    private Draft findDraft(Long draftId) {
        if (draftId == null) {
            throw new BadRequestException("draftId is required");
        }
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft not found"));
    }

    private String normalizeAndAppendSignature(String reply, String signature) {
        String out = (reply == null) ? "" : reply.stripTrailing();
        String sig = (signature == null) ? "" : signature.trim();
        if (sig.isBlank()) return out;

        // If the model already wrote a quick one-line signature like:
        // Thanks.
        //
        // Name,phone,email
        // strip it when it contains an email/phone (so we only keep the saved signature).
        String emailInSig = extractEmail(sig);
        String phoneInSig = extractPhone(sig);
        out = stripCommaSignatureBlock(out, emailInSig, phoneInSig);
        out = stripCommaContactLinesNearEnd(out, emailInSig, phoneInSig);

        // Remove any trailing valedictions added by the model so we keep only the user's real signature.
        out = stripTrailingClosings(out);

        // Avoid double-append: if the reply already contains the signature email, assume signature is present.
        if (!emailInSig.isBlank() && out.toLowerCase().contains(emailInSig.toLowerCase())) {
            return out;
        }

        return out + "\n\n" + sig;
    }

    private String stripCommaContactLinesNearEnd(String text, String email, String phone) {
        if (text == null || text.isBlank()) return text;
        String emailLc = (email == null) ? "" : email.toLowerCase();
        String phoneDigits = (phone == null) ? "" : phone.replaceAll("\\D+", "");

        String[] lines = text.split("\\R", -1);
        if (lines.length == 0) return text;

        // Only scrub near the end to avoid accidentally removing valid content.
        int start = Math.max(0, lines.length - 25);
        boolean changed = false;

        Pattern commaContact = Pattern.compile(
                "(?i)^\\s*[^\\n,]{2,}\\s*,\\s*\\+?\\d[\\d\\s\\-()]{6,}\\s*,\\s*[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\s*$"
        );

        for (int i = start; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) continue;
            if (!line.contains(",")) continue;

            String lc = line.toLowerCase();
            String digits = line.replaceAll("\\D+", "");

            boolean matchesEmail = !emailLc.isBlank() && lc.contains(emailLc);
            boolean matchesPhone = !phoneDigits.isBlank() && digits.contains(phoneDigits);

            // Remove either:
            // - an exact "name,phone,email" contact line, OR
            // - any comma line that matches the user's email/phone.
            if (commaContact.matcher(line).matches() || matchesEmail || matchesPhone) {
                lines[i] = "";
                changed = true;
            }
        }

        if (!changed) return text;

        // Rebuild, collapsing extra blank lines.
        String rebuilt = String.join("\n", lines).replaceAll("\\n{3,}", "\n\n").stripTrailing();
        return rebuilt;
    }

    private String stripTrailingClosings(String text) {
        if (text == null) return "";
        String t = text.stripTrailing();

        // Remove repeated closing lines at the end, e.g.:
        // Thanks,
        // Best regards,
        // Regards,
        // Thanks & Regards,
        //
        // (We remove them so the stored signature provides the single closing.)
        Pattern closingLine = Pattern.compile(
                "^(?i)\\s*(thanks|thank you|regards|best regards|kind regards|warm regards|thanks\\s*&\\s*regards|thanks\\s+and\\s+regards)[\\s\\.,]*$"
        );

        while (true) {
            String[] lines = t.split("\\R", -1);
            int i = lines.length - 1;
            // skip empty tail
            while (i >= 0 && lines[i].trim().isEmpty()) i--;
            if (i < 0) return "";

            String last = lines[i].trim();
            if (!closingLine.matcher(last).matches()) {
                return t;
            }

            // drop last non-empty closing line
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < i; j++) {
                sb.append(lines[j]).append("\n");
            }
            t = sb.toString().stripTrailing();
        }
    }

    private String stripCommaSignatureBlock(String text, String email, String phone) {
        if (text == null || text.isBlank()) return text;
        String t = text.stripTrailing();
        String emailLc = email == null ? "" : email.toLowerCase();
        String phoneDigits = (phone == null) ? "" : phone.replaceAll("\\D+", "");

        // Remove a trailing "name,phone,email" style line (with or without a preceding Thanks line),
        // but only when it matches the user's email/phone from the saved signature.
        String[] lines = t.split("\\R", -1);
        int i = lines.length - 1;
        while (i >= 0 && lines[i].trim().isEmpty()) i--;
        if (i < 0) return "";

        String lastLine = lines[i].trim();
        String lastLineLc = lastLine.toLowerCase();
        String lastLineDigits = lastLine.replaceAll("\\D+", "");

        boolean matchesEmail = !emailLc.isBlank() && lastLineLc.contains(emailLc);
        boolean matchesPhone = !phoneDigits.isBlank() && lastLineDigits.contains(phoneDigits);

        if ((matchesEmail || matchesPhone) && lastLine.contains(",")) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < i; j++) sb.append(lines[j]).append("\n");
            return sb.toString().stripTrailing();
        }

        return t;
    }

    private String extractEmail(String signature) {
        if (signature == null) return "";
        Matcher m = Pattern.compile("([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})", Pattern.CASE_INSENSITIVE).matcher(signature);
        return m.find() ? m.group(1) : "";
    }

    private String extractPhone(String signature) {
        if (signature == null) return "";
        // Loose match for a phone-like sequence (7+ digits allowing +, spaces, hyphens, parentheses).
        Matcher m = Pattern.compile("(\\+?\\d[\\d\\s\\-()]{6,}\\d)").matcher(signature);
        return m.find() ? m.group(1) : "";
    }
    private String buildToneContext(List<String> sentEmails) {

        return sentEmails.stream()
                .limit(3)
                .map(e -> "Example reply:\n" + e)
                .collect(Collectors.joining("\n\n"));
    }
}
