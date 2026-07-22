package com.puduvandi.superadmin.controller;

import com.puduvandi.admin.dto.AdminDataResetResponse;
import com.puduvandi.admin.dto.AdminUpdateUserRequest;
import com.puduvandi.admin.dto.AdminUserResponse;
import com.puduvandi.admin.dto.CommissionResponse;
import com.puduvandi.admin.dto.ResetLocalDataRequest;
import com.puduvandi.admin.dto.UpdateCommissionRequest;
import com.puduvandi.admin.service.AdminService;
import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.common.enums.UserRole;
import com.puduvandi.common.enums.UserStatus;
import com.puduvandi.delivery.dto.DeliveryRateResponse;
import com.puduvandi.delivery.dto.UpdateDeliveryRateRequest;
import com.puduvandi.security.PuduvandiUserPrincipal;
import com.puduvandi.superadmin.dto.CreateAdminRequest;
import com.puduvandi.superadmin.dto.TableDataResponse;
import com.puduvandi.superadmin.dto.TableSummaryResponse;
import com.puduvandi.superadmin.service.SuperAdminAccountService;
import com.puduvandi.superadmin.service.SuperAdminDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin", description = "Live database explorer, admin-account management, and platform settings — super admin only")
@SecurityRequirement(name = "bearerAuth")
public class SuperAdminController {

    private final SuperAdminDataService dataService;
    private final SuperAdminAccountService accountService;
    private final AdminService adminService;

    // ===== GENERIC DATABASE EXPLORER =====

    @GetMapping("/tables")
    @Operation(summary = "List every table in the database, read live from information_schema")
    public ResponseEntity<ApiResponse<List<TableSummaryResponse>>> listTables() {
        return ResponseEntity.ok(ApiResponse.success("Tables fetched", dataService.listTables()));
    }

    @GetMapping("/tables/{tableName}")
    @Operation(summary = "Get a table's column metadata and a page of its raw rows")
    public ResponseEntity<ApiResponse<TableDataResponse>> getTableData(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Table data fetched", dataService.getTableData(tableName, page, size)));
    }

    @PutMapping("/tables/{tableName}/rows/{pkValue}")
    @Operation(summary = "Edit a row's non-sensitive, non-primary-key columns")
    public ResponseEntity<ApiResponse<Void>> updateRow(
            @PathVariable String tableName,
            @PathVariable String pkValue,
            @RequestBody Map<String, Object> changes) {
        dataService.updateRow(tableName, pkValue, changes);
        return ResponseEntity.ok(ApiResponse.success("Row updated"));
    }

    @DeleteMapping("/tables/{tableName}/rows/{pkValue}")
    @Operation(summary = "Hard-delete a row by primary key")
    public ResponseEntity<ApiResponse<Void>> deleteRow(
            @PathVariable String tableName,
            @PathVariable String pkValue) {
        dataService.deleteRow(tableName, pkValue);
        return ResponseEntity.ok(ApiResponse.success("Row deleted"));
    }

    @PostMapping("/tables/{tableName}/columns/{columnName}/make-unique")
    @Operation(summary = "Add a UNIQUE constraint to a column, after checking for existing duplicate values")
    public ResponseEntity<ApiResponse<Void>> makeColumnUnique(
            @PathVariable String tableName,
            @PathVariable String columnName) {
        dataService.makeColumnUnique(tableName, columnName);
        return ResponseEntity.ok(ApiResponse.success("Column '" + columnName + "' is now unique"));
    }

    // ===== ADMIN ACCOUNT MANAGEMENT =====

    @GetMapping("/admins")
    @Operation(summary = "List all ADMIN and SUPER_ADMIN accounts")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> listAdmins(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Admins fetched", accountService.listAdmins(page, size)));
    }

    @PostMapping("/admins")
    @Operation(summary = "Create a new ADMIN account (role is always ADMIN)")
    public ResponseEntity<ApiResponse<AdminUserResponse>> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Admin created", accountService.createAdmin(request)));
    }

    @DeleteMapping("/admins/{userId}")
    @Operation(summary = "Soft-delete an ADMIN account")
    public ResponseEntity<ApiResponse<Void>> deleteAdmin(@PathVariable Long userId) {
        accountService.deleteAdmin(userId);
        return ResponseEntity.ok(ApiResponse.success("Admin removed"));
    }

    // ===== USER ACCOUNT MANAGEMENT (moved from /admin — user PII + account deletes are super-admin only) =====

    @GetMapping("/users")
    @Operation(summary = "List all users (paginated, filter by role/status)")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success("Users fetched", adminService.listUsers(role, status, page, size)));
    }

    @PutMapping("/users/{userId}")
    @Operation(summary = "Edit a user's basic profile fields")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User updated", adminService.updateUser(userId, request)));
    }

    @PatchMapping("/users/{userId}/suspend")
    @Operation(summary = "Suspend a user account")
    public ResponseEntity<ApiResponse<AdminUserResponse>> suspendUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User suspended", adminService.suspendUser(userId)));
    }

    @PatchMapping("/users/{userId}/unsuspend")
    @Operation(summary = "Unsuspend a user account")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unsuspendUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User unsuspended", adminService.unsuspendUser(userId)));
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Soft-delete a user account (preserves booking history)")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.success("User deleted", null));
    }

    // ===== DESTRUCTIVE OPERATIONS (moved from /admin — deletes are super-admin only) =====

    @DeleteMapping("/owners/{ownerId}")
    @Operation(summary = "Soft-delete an owner profile (cascades to their bikes)")
    public ResponseEntity<ApiResponse<Void>> deleteOwner(@PathVariable Long ownerId) {
        adminService.deleteOwner(ownerId);
        return ResponseEntity.ok(ApiResponse.success("Owner deleted", null));
    }

    @DeleteMapping("/partners/{partnerId}")
    @Operation(summary = "Soft-delete a delivery partner profile")
    public ResponseEntity<ApiResponse<Void>> deletePartner(@PathVariable Long partnerId) {
        adminService.deletePartner(partnerId);
        return ResponseEntity.ok(ApiResponse.success("Partner deleted", null));
    }

    @DeleteMapping("/bikes/{bikeId}")
    @Operation(summary = "Soft-delete a bike listing")
    public ResponseEntity<ApiResponse<Void>> deleteBike(@PathVariable Long bikeId) {
        adminService.deleteBike(bikeId);
        return ResponseEntity.ok(ApiResponse.success("Bike deleted", null));
    }

    // ===== PLATFORM SETTINGS (moved off the regular admin panel) =====

    @GetMapping("/commission")
    @Operation(summary = "Get current commission settings")
    public ResponseEntity<ApiResponse<CommissionResponse>> getCommission() {
        return ResponseEntity.ok(ApiResponse.success("Commission fetched", adminService.getActiveCommission()));
    }

    @PutMapping("/commission")
    @Operation(summary = "Update platform commission percentage")
    public ResponseEntity<ApiResponse<CommissionResponse>> updateCommission(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody UpdateCommissionRequest request) {
        CommissionResponse response = adminService.updateCommission(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Commission updated to " + request.commissionPercent() + "%", response));
    }

    @GetMapping("/delivery-rate")
    @Operation(summary = "Get current partner delivery rate settings")
    public ResponseEntity<ApiResponse<DeliveryRateResponse>> getDeliveryRate() {
        return ResponseEntity.ok(ApiResponse.success("Delivery rate fetched", adminService.getActiveDeliveryRate()));
    }

    @PutMapping("/delivery-rate")
    @Operation(summary = "Update the platform's per-km partner delivery rate")
    public ResponseEntity<ApiResponse<DeliveryRateResponse>> updateDeliveryRate(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody UpdateDeliveryRateRequest request) {
        DeliveryRateResponse response = adminService.updateDeliveryRate(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Delivery rate updated to ₹" + request.ratePerKm() + "/km", response));
    }

    @PostMapping("/reset-local-data")
    @Operation(summary = "DANGER: wipe all owners/customers/partners/bikes/bookings, keep only ADMIN/SUPER_ADMIN users. " +
            "Blocked unless PUDUVANDI_ENV is unset or \"local\" — never staging/production.")
    public ResponseEntity<ApiResponse<AdminDataResetResponse>> resetLocalData(@Valid @RequestBody ResetLocalDataRequest request) {
        AdminDataResetResponse response = adminService.resetLocalData(request.confirmationPhrase());
        return ResponseEntity.ok(ApiResponse.success("Local data reset complete", response));
    }
}
