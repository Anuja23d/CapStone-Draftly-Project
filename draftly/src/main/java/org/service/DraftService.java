package org.service;

import org.entity.Draft;

import java.util.List;

public interface DraftService {
    List<Draft> getAllDrafts();

    List<Draft> getDraftsByStatus(String status);

    Draft generateDraft(String body, String userEmail) throws Exception;

    Draft generateDraftForEmail(Long emailId, String userEmail) throws Exception;

    Draft editDraft(Long draftId, String editedText);

    Draft approveDraft(Long draftId);

    Draft rejectDraft(Long draftId);

    Draft sendApprovedDraft(Long draftId, String email, String idempotencyKey) throws Exception;
}
