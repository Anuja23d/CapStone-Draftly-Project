package org.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@Data
public class Draft {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Email email;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String generatedText;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String editedText;

    private String status; // PENDING, APPROVED, REJECTED, SENT, FAILED

    // Idempotency key for sending (prevents duplicate sends)
    private String idempotencyKey;

    // Gmail message id of sent reply (if sent)
    private String sentMessageId;
}