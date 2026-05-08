package org.dto;

import lombok.Data;

@Data
public class DraftGenerateForEmailRequest {
    private String email;      // Draftly user email
    private Long emailId;      // Email table id (preferred)
    private String messageId;  // Gmail API messageId (optional alternative)
}

