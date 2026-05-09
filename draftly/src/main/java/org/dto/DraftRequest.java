package org.dto;

import lombok.Data;

@Data
public class DraftRequest {
    private  String email;
    private String body; // optional (future use)
}