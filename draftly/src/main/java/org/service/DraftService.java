package org.service;

import org.entity.Draft;
import org.entity.Email;
import org.entity.User;

public interface DraftService {
    Draft generateDraft(String body, String userEmail) throws Exception;

    Draft generateDraftForEmail(Long emailId, String userEmail) throws Exception;
}
