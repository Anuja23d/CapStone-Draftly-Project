package org.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Log {

    @Id
    @GeneratedValue
    private Long id;

    private Long draftId;

    private String status;

    private String message;

    private LocalDateTime timestamp;
}
