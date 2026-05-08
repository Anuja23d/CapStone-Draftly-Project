package org.dto;

import lombok.Data;

@Data
public class PreferencesRequest {
    private String email;
    private String tone; // DEFAULT, FORMAL, FRIENDLY, CONCISE
}

