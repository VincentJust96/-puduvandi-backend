package com.puduvandi.common.enums;

/** What a Payment attempt is for — see PaymentService for the amount/eligibility rules for each. */
public enum PaymentType {
    /** 10% of the booking's total, paid at booking time under the "pay later" plan. */
    DEPOSIT,
    /** 100% of the booking's total, paid at booking time. */
    FULL,
    /** The remaining balance on a booking that was booked under the DEPOSIT plan. */
    BALANCE
}
