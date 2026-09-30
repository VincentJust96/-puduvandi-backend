package com.puduvandi.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Optional PDF password, only needed when a prior call returned passwordRequired=true.")
public record InsuranceAnalyzeRequest(String password) {}
