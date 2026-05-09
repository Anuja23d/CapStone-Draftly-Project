package org.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Data;

@Entity
@Data
public class User {

    @Id
    @GeneratedValue
    private Long id;

    private String email;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String accessToken;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String signature;
}