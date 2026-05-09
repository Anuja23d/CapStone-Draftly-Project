package org.dto;

import lombok.Data;

@Data
public class ReplySendRequest {
    // The Draftly user email (used to find stored Google OAuth tokens)
    private String email;

    // Gmail message id (the API message id of the email you are replying to)
    private String messageId;

    // Reply body (plain text)
    private String replyText;
}

