package com.puduvandi.common.enums;

/**
 * Types of documents accepted on the platform.
 * Used by both customers (driving license) and owners (KYC docs).
 */
public enum DocumentType {
    AADHAAR,
    PAN,
    DRIVING_LICENSE,
    VEHICLE_RC,
    VEHICLE_INSURANCE,
    BANK_PASSBOOK
}
