package com.puduvandi.superadmin.service;

import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ConflictException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.superadmin.dto.ColumnMetaResponse;
import com.puduvandi.superadmin.dto.TableDataResponse;
import com.puduvandi.superadmin.dto.TableSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic, live database explorer for the super admin panel. Every table and
 * column shown here is read straight from Postgres' information_schema on
 * each call — nothing is hardcoded per entity — which is also why every
 * identifier that ends up in a raw SQL string is first checked against a
 * freshly-read schema snapshot: JDBC can bind values as {@code ?} params but
 * never identifiers, so that schema check is the only thing standing between
 * a table/column name and SQL injection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminDataService {

    private final JdbcTemplate jdbcTemplate;

    /** Editing Flyway's own bookkeeping table is not "generic DB admin" — it's guaranteed breakage. */
    private static final Set<String> EXCLUDED_TABLES = Set.of("flyway_schema_history");

    private static final List<String> SENSITIVE_NAME_FRAGMENTS =
            List.of("password", "otp", "token", "secret");

    private static final String MASKED_VALUE = "••••••••";

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 200;

    @Transactional(readOnly = true)
    public List<TableSummaryResponse> listTables() {
        List<TableSummaryResponse> result = new ArrayList<>();
        for (String table : fetchTableNames()) {
            Long rowCount = jdbcTemplate.queryForObject("SELECT count(*) FROM \"" + table + "\"", Long.class);
            boolean editable = findPrimaryKeyColumn(table).isPresent();
            result.add(new TableSummaryResponse(table, rowCount == null ? 0 : rowCount, editable));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public TableDataResponse getTableData(String tableName, int page, int size) {
        String table = validateTable(tableName);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);

        List<ColumnMetaResponse> columns = getColumns(table);
        Optional<String> pk = findPrimaryKeyColumn(table);
        boolean editable = pk.isPresent();

        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM \"" + table + "\"", Long.class);

        String orderBy = pk.map(c -> " ORDER BY \"" + c + "\"").orElse("");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM \"" + table + "\"" + orderBy + " LIMIT ? OFFSET ?",
                safeSize, safePage * safeSize);

        Set<String> sensitiveCols = columns.stream()
                .filter(ColumnMetaResponse::sensitive)
                .map(ColumnMetaResponse::name)
                .collect(Collectors.toSet());
        List<Map<String, Object>> maskedRows = rows.stream().map(row -> maskRow(row, sensitiveCols)).toList();

        return new TableDataResponse(table, columns, maskedRows, total == null ? 0 : total, safePage, safeSize, editable);
    }

    @Transactional
    public void updateRow(String tableName, String pkValue, Map<String, Object> changes) {
        String table = validateTable(tableName);
        if (changes == null || changes.isEmpty()) {
            throw new BusinessException("No changes supplied");
        }

        String pk = requireSingleColumnPrimaryKey(table);
        Map<String, ColumnMetaResponse> columnsByName = getColumns(table).stream()
                .collect(Collectors.toMap(ColumnMetaResponse::name, c -> c));

        StringBuilder sql = new StringBuilder("UPDATE \"").append(table).append("\" SET ");
        List<Object> params = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            ColumnMetaResponse meta = columnsByName.get(entry.getKey());
            if (meta == null) {
                throw new BusinessException("Unknown column '" + entry.getKey() + "' on table '" + table + "'");
            }
            if (meta.sensitive()) {
                throw new ForbiddenException("Column '" + entry.getKey() + "' cannot be edited");
            }
            if (meta.primaryKey()) {
                throw new BusinessException("Primary key column '" + entry.getKey() + "' cannot be edited");
            }
            if (!first) sql.append(", ");
            sql.append('"').append(meta.name()).append("\" = ?::").append(meta.dataType());
            params.add(entry.getValue());
            first = false;
        }
        // The WHERE parameter needs the same explicit cast as SET values — without
        // it Postgres can't always infer the bound param's type against a non-text
        // PK column (confirmed live: "operator does not exist: bigint = character
        // varying" on an integer PK before this cast was added).
        sql.append(" WHERE \"").append(pk).append("\" = ?::").append(columnsByName.get(pk).dataType());
        params.add(pkValue);

        int updated;
        try {
            updated = jdbcTemplate.update(sql.toString(), params.toArray());
        } catch (DataAccessException ex) {
            throw new ConflictException("Update failed: " + rootMessage(ex));
        }
        if (updated == 0) {
            throw new ResourceNotFoundException("Row with " + pk + "=" + pkValue + " not found in " + table);
        }
        log.warn("Super admin updated table={} pk={} columns={}", table, pkValue, changes.keySet());
    }

    @Transactional
    public void deleteRow(String tableName, String pkValue) {
        String table = validateTable(tableName);
        String pk = requireSingleColumnPrimaryKey(table);
        String pkDataType = getColumns(table).stream()
                .filter(ColumnMetaResponse::primaryKey)
                .findFirst()
                .map(ColumnMetaResponse::dataType)
                .orElseThrow(() -> new BusinessException("Table '" + table + "' has no single-column primary key"));

        int deleted;
        try {
            deleted = jdbcTemplate.update(
                    "DELETE FROM \"" + table + "\" WHERE \"" + pk + "\" = ?::" + pkDataType, pkValue);
        } catch (DataAccessException ex) {
            throw new ConflictException("Cannot delete row: " + rootMessage(ex));
        }
        if (deleted == 0) {
            throw new ResourceNotFoundException("Row with " + pk + "=" + pkValue + " not found in " + table);
        }
        log.warn("Super admin deleted row table={} pk={}", table, pkValue);
    }

    @Transactional
    public void makeColumnUnique(String tableName, String columnName) {
        String table = validateTable(tableName);
        ColumnMetaResponse meta = getColumns(table).stream()
                .filter(c -> c.name().equals(columnName))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Column '" + columnName + "' not found on table '" + table + "'"));

        if (meta.sensitive()) {
            throw new ForbiddenException("Column '" + columnName + "' cannot be altered");
        }
        if (meta.unique()) {
            throw new BusinessException("Column '" + columnName + "' is already unique");
        }

        List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(
                "SELECT \"" + columnName + "\" AS val, count(*) AS cnt FROM \"" + table + "\" " +
                "WHERE \"" + columnName + "\" IS NOT NULL GROUP BY \"" + columnName + "\" HAVING count(*) > 1 LIMIT 5");

        if (!duplicates.isEmpty()) {
            String sample = duplicates.stream()
                    .map(r -> r.get("val") + " (" + r.get("cnt") + "x)")
                    .collect(Collectors.joining(", "));
            throw new ConflictException(
                    "Cannot make '" + columnName + "' unique — duplicate values exist: " + sample);
        }

        String constraintName = table + "_" + columnName + "_unique";
        try {
            jdbcTemplate.execute("ALTER TABLE \"" + table + "\" ADD CONSTRAINT \"" + constraintName
                    + "\" UNIQUE (\"" + columnName + "\")");
        } catch (DataAccessException ex) {
            throw new ConflictException("Could not add unique constraint: " + rootMessage(ex));
        }
        log.warn("Super admin added UNIQUE constraint on {}.{}", table, columnName);
    }

    // ===== Schema introspection helpers =====

    private List<String> fetchTableNames() {
        List<String> names = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                String.class);
        names.removeIf(EXCLUDED_TABLES::contains);
        return names;
    }

    private String validateTable(String tableName) {
        if (tableName == null || !fetchTableNames().contains(tableName)) {
            throw new ResourceNotFoundException("Table '" + tableName + "' does not exist");
        }
        return tableName;
    }

    private String requireSingleColumnPrimaryKey(String table) {
        return findPrimaryKeyColumn(table)
                .orElseThrow(() -> new BusinessException(
                        "Table '" + table + "' has no single-column primary key and cannot be edited"));
    }

    private Optional<String> findPrimaryKeyColumn(String table) {
        List<String> pkCols = jdbcTemplate.queryForList(
                "SELECT kcu.column_name FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu " +
                "  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema " +
                "WHERE tc.table_schema = 'public' AND tc.table_name = ? AND tc.constraint_type = 'PRIMARY KEY' " +
                "ORDER BY kcu.ordinal_position",
                String.class, table);
        return pkCols.size() == 1 ? Optional.of(pkCols.get(0)) : Optional.empty();
    }

    private Set<String> findSingleColumnUniqueColumns(String table) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT tc.constraint_name AS cn, kcu.column_name AS col " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu " +
                "  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema " +
                "WHERE tc.table_schema = 'public' AND tc.table_name = ? AND tc.constraint_type = 'UNIQUE'",
                table);
        Map<String, List<String>> byConstraint = rows.stream().collect(Collectors.groupingBy(
                r -> (String) r.get("cn"),
                Collectors.mapping(r -> (String) r.get("col"), Collectors.toList())));
        Set<String> result = new HashSet<>();
        for (List<String> cols : byConstraint.values()) {
            if (cols.size() == 1) result.add(cols.get(0));
        }
        return result;
    }

    private List<ColumnMetaResponse> getColumns(String table) {
        List<Map<String, Object>> colRows = jdbcTemplate.queryForList(
                "SELECT column_name, udt_name, is_nullable FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position",
                table);
        Set<String> pkCols = findPrimaryKeyColumn(table).map(Set::of).orElseGet(Set::of);
        Set<String> uniqueCols = findSingleColumnUniqueColumns(table);

        List<ColumnMetaResponse> result = new ArrayList<>();
        for (Map<String, Object> row : colRows) {
            String name = (String) row.get("column_name");
            String dataType = (String) row.get("udt_name");
            boolean nullable = "YES".equals(row.get("is_nullable"));
            boolean primaryKey = pkCols.contains(name);
            boolean unique = primaryKey || uniqueCols.contains(name);
            result.add(new ColumnMetaResponse(name, dataType, nullable, primaryKey, unique, isSensitive(name)));
        }
        return result;
    }

    private boolean isSensitive(String columnName) {
        String lower = columnName.toLowerCase(Locale.ROOT);
        return SENSITIVE_NAME_FRAGMENTS.stream().anyMatch(lower::contains);
    }

    private Map<String, Object> maskRow(Map<String, Object> row, Set<String> sensitiveCols) {
        if (sensitiveCols.isEmpty()) return row;
        Map<String, Object> copy = new LinkedHashMap<>(row);
        for (String col : sensitiveCols) {
            if (copy.get(col) != null) copy.put(col, MASKED_VALUE);
        }
        return copy;
    }

    private String rootMessage(DataAccessException ex) {
        Throwable cause = ex.getMostSpecificCause();
        return cause != null ? cause.getMessage() : ex.getMessage();
    }
}
