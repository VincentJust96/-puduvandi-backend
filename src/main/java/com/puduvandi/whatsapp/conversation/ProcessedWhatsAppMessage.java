package com.puduvandi.whatsapp.conversation;

import com.puduvandi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Marker row per inbound wamid so a redelivered webhook is never handled twice. */
@Entity
@Table(name = "whatsapp_processed_messages")
@Getter
@NoArgsConstructor
public class ProcessedWhatsAppMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 200)
    private String messageId;

    public ProcessedWhatsAppMessage(String messageId) {
        this.messageId = messageId;
    }
}
