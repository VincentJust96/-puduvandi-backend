package com.puduvandi.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Best-effort policy number / valid-till date read from an uploaded insurance PDF. " +
        "Either field may be null if it couldn't be found in the document — the caller should let the " +
        "user fill or correct it manually in that case. When passwordRequired is true the PDF is " +
        "encrypted and policyNumber/validTill are always null — the caller must re-submit with the " +
        "PDF's password before any extraction is attempted.")
public record InsuranceDetailsResponse(
    String policyNumber,
    LocalDate validTill,
    boolean passwordRequired
) {}
