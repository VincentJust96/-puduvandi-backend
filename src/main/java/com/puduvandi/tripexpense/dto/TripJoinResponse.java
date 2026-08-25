package com.puduvandi.tripexpense.dto;

/** Result of joining a trip via its shareable invite link — just enough for the frontend to navigate to the dashboard. */
public record TripJoinResponse(
    Long tripId,
    String tripName
) {}
