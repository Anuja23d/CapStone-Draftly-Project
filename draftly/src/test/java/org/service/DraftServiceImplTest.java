package org.service;

import org.entity.Draft;
import org.entity.Email;
import org.entity.User;
import org.exception.BadRequestException;
import org.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.repository.DraftRepository;
import org.repository.EmailRepository;
import org.repository.UserPreferencesRepository;
import org.repository.UserRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftServiceImplTest {

    @Mock
    private AIService aiService;

    @Mock
    private DraftRepository draftRepository;

    @Mock
    private EmailRepository emailRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferencesRepository preferencesRepository;

    @Mock
    private LogService logService;

    private DraftServiceImpl draftService;

    @BeforeEach
    void setUp() {
        draftService = new DraftServiceImpl();
        ReflectionTestUtils.setField(draftService, "aiService", aiService);
        ReflectionTestUtils.setField(draftService, "draftRepository", draftRepository);
        ReflectionTestUtils.setField(draftService, "emailRepository", emailRepository);
        ReflectionTestUtils.setField(draftService, "emailService", emailService);
        ReflectionTestUtils.setField(draftService, "userRepository", userRepository);
        ReflectionTestUtils.setField(draftService, "preferencesRepository", preferencesRepository);
        ReflectionTestUtils.setField(draftService, "logService", logService);
    }

    @Test
    void approveDraft_updatesStatusAndLogsAction() {
        Draft draft = new Draft();
        when(draftRepository.findById(10L)).thenReturn(Optional.of(draft));
        when(draftRepository.save(draft)).thenReturn(draft);

        Draft result = draftService.approveDraft(10L);

        assertSame(draft, result);
        assertEquals("APPROVED", result.getStatus());
        verify(logService).log(10L, "APPROVED", "Draft approved");
        verify(draftRepository).save(draft);
    }

    @Test
    void rejectDraft_updatesStatusAndLogsAction() {
        Draft draft = new Draft();
        when(draftRepository.findById(11L)).thenReturn(Optional.of(draft));
        when(draftRepository.save(draft)).thenReturn(draft);

        Draft result = draftService.rejectDraft(11L);

        assertSame(draft, result);
        assertEquals("REJECTED", result.getStatus());
        verify(logService).log(11L, "REJECTED", "Draft rejected");
        verify(draftRepository).save(draft);
    }

    @Test
    void editDraft_setsEditedTextAndMovesDraftBackToPending() {
        Draft draft = new Draft();
        draft.setStatus("APPROVED");
        when(draftRepository.findById(12L)).thenReturn(Optional.of(draft));
        when(draftRepository.save(draft)).thenReturn(draft);

        Draft result = draftService.editDraft(12L, "Updated reply");

        assertEquals("Updated reply", result.getEditedText());
        assertEquals("PENDING", result.getStatus());
        verify(draftRepository).save(draft);
    }

    @Test
    void sendApprovedDraft_returnsExistingDraftForRepeatedIdempotencyKey() throws Exception {
        Draft draft = new Draft();
        draft.setStatus("APPROVED");

        Draft existing = new Draft();
        existing.setStatus("SENT");

        when(draftRepository.findById(13L)).thenReturn(Optional.of(draft));
        when(draftRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        Draft result = draftService.sendApprovedDraft(13L, "user@gmail.com", "key-1");

        assertSame(existing, result);
        verify(userRepository, never()).findByEmail(any());
        verify(emailService, never()).sendReply(any(), any(), any());
    }

    @Test
    void sendApprovedDraft_rejectsDraftThatIsNotApproved() {
        Draft draft = new Draft();
        draft.setStatus("PENDING");
        when(draftRepository.findById(14L)).thenReturn(Optional.of(draft));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> draftService.sendApprovedDraft(14L, "user@gmail.com", "key-2")
        );

        assertEquals("Draft must be APPROVED before sending", ex.getMessage());
    }

    @Test
    void sendApprovedDraft_sendsReplyAndMarksDraftSent() throws Exception {
        User user = new User();
        user.setEmail("user@gmail.com");

        Email email = new Email();
        email.setMessageId("gmail-message-1");

        Draft draft = new Draft();
        draft.setStatus("APPROVED");
        draft.setEmail(email);
        draft.setEditedText("Edited reply");

        when(draftRepository.findById(15L)).thenReturn(Optional.of(draft));
        when(draftRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(emailService.sendReply(user, "gmail-message-1", "Edited reply")).thenReturn("sent-message-1");
        when(draftRepository.save(draft)).thenReturn(draft);

        Draft result = draftService.sendApprovedDraft(15L, "user@gmail.com", "key-3");

        assertEquals("SENT", result.getStatus());
        assertEquals("sent-message-1", result.getSentMessageId());
        assertEquals("key-3", result.getIdempotencyKey());
        verify(logService).log(15L, "SEND_ATTEMPT", "Attempting send");
        verify(logService).log(15L, "SENT", "Sent via Gmail API");
    }

    @Test
    void findDraftFailureBecomesNotFound() {
        when(draftRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> draftService.approveDraft(99L)
        );

        assertEquals("Draft not found", ex.getMessage());
    }
}
