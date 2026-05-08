package org.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class UserPreferences {

    @Id
    @GeneratedValue
    private Long id;

    @OneToOne
    private User user;

    // DEFAULT, FORMAL, FRIENDLY, CONCISE
    private String tone = "DEFAULT";
}

