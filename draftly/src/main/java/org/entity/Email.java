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
public class Email {

    @Id
    @GeneratedValue
    private Long id;

    private String subject;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String body;

    private String sender;

    // Gmail API message id (required for threading + reply sending)
    private String messageId;

    private String threadId;

    private String label; // e.g. INBOX, SENT

    private String snippet;

    @ManyToOne
    private User user;
}
