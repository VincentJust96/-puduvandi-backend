package com.puduvandi.whatsapp.conversation;

/** Where a customer is in the booking chat. START means "no booking in progress". */
public enum ConversationState {
    START,
    CHOOSING_LOCATION,
    CHOOSING_MODE,
    ASKING_FROM,
    ASKING_TO,
    CHOOSING_BIKE,
    CONFIRMING,
    /** Booking confirmed but no driving licence on file yet — waiting for a photo of it. */
    AWAITING_LICENCE
}
