package org.dto;

import lombok.Data;

@Data
public class DraftSendRequest {
    private String email; // Draftly user email
    private String idempotencyKey; // required to avoid duplicate sends
}

