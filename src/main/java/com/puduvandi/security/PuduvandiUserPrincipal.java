package com.puduvandi.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents the authenticated user stored in the Spring Security context.
 * Accessible from any controller via @AuthenticationPrincipal.
 * <p>
 * Example usage in controller:
 * <pre>
 *   public ResponseEntity<?> myEndpoint(
 *       @AuthenticationPrincipal PuduvandiUserPrincipal principal) {
 *       Long userId = principal.getUserId();
 *   }
 * </pre>
 */
@Getter
@AllArgsConstructor
public class PuduvandiUserPrincipal {

    /** Database ID of the authenticated user */
    private final Long userId;

    /** Phone number used as the login identifier */
    private final String phone;

    /** Role string: CUSTOMER / OWNER / ADMIN */
    private final String role;
}
