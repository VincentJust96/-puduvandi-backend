package com.puduvandi.whatsapp.conversation;

import com.puduvandi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One row per WhatsApp number: the answers collected so far in the booking chat. */
@Entity
@Table(name = "whatsapp_sessions")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** WhatsApp id = phone number with country code, digits only (e.g. 919876543210). */
    @Column(name = "wa_id", nullable = false, unique = true, length = 20)
    private String waId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private ConversationState state = ConversationState.START;

    @Column(name = "area", length = 150)
    private String area;

    /** HOUR or DAY */
    @Column(name = "rental_mode", length = 10)
    private String rentalMode;

    @Column(name = "pickup_datetime")
    private LocalDateTime pickupDatetime;

    @Column(name = "return_datetime")
    private LocalDateTime returnDatetime;

    @Column(name = "bike_id")
    private Long bikeId;

    @Column(name = "last_interaction_at", nullable = false)
    private LocalDateTime lastInteractionAt = LocalDateTime.now();

    public WhatsAppSession(String waId) {
        this.waId = waId;
    }

    /** Forget everything collected so far and go back to the start. */
    public void reset() {
        state = ConversationState.START;
        area = null;
        rentalMode = null;
        pickupDatetime = null;
        returnDatetime = null;
        bikeId = null;
    }
}
