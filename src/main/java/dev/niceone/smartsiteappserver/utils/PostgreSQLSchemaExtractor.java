package dev.niceone.smartsiteappserver.utils;

import java.sql.*;
import java.util.*;

/**
 * Standalone utility class to connect to PostgreSQL database and extract complete schema information.
 * This utility reads all table names, column information, constraints, and relationships from the specified schema.
 */
public class PostgreSQLSchemaExtractor {
    
    private static final String DB_HOST = env("SSK_DB_HOST", "127.0.0.1");
    private static final String DB_PORT = env("SSK_DB_PORT", "5432");
    private static final String DB_NAME = env("SSK_DB_NAME", "smartsite2");
    private static final String DB_USERNAME = env("SSK_DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = env("SSK_DB_PASSWORD", "");
    private static final String SCHEMA_NAME = "smartsite";
    
    private Connection connection;

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }

    public static void main(String[] args) {
        PostgreSQLSchemaExtractor extractor = new PostgreSQLSchemaExtractor();
        try {
            extractor.connect();
            extractor.extractAndPrintSchema();
        } catch (Exception e) {
            System.err.println("Error occurred while extracting schema: " + e.getMessage());
            e.printStackTrace();
        } finally {
            extractor.disconnect();
        }
    }
    
    /**
     * Establishes connection to the PostgreSQL database
     */
    private void connect() throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%s/%s", DB_HOST, DB_PORT, DB_NAME);
        
        try {
            // Load PostgreSQL JDBC driver
            Class.forName("org.postgresql.Driver");
            
            // Establish connection
            connection = DriverManager.getConnection(url, DB_USERNAME, DB_PASSWORD);
            System.out.println("SUCCESS: Connected to PostgreSQL database: " + DB_NAME);
            System.out.println("SUCCESS: Target schema: " + SCHEMA_NAME);
            System.out.println();
            
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC Driver not found", e);
        }
    }
    
    /**
     * Closes the database connection
     */
    private void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("\nSUCCESS: Database connection closed successfully");
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Extracts and prints the complete database schema information
     */
    private void extractAndPrintSchema() throws SQLException {
        String separator = new String(new char[80]).replace("\0", "=");
        System.out.println(separator);
        System.out.println("POSTGRESQL DATABASE SCHEMA REPORT");
        System.out.println("Database: " + DB_NAME + " | Schema: " + SCHEMA_NAME);
        System.out.println("Generated: " + new java.util.Date());
        System.out.println(separator);
        System.out.println();
        
        // Get all tables in the schema
        List<String> tableNames = getAllTables();
        
        if (tableNames.isEmpty()) {
            System.out.println("No tables found in schema: " + SCHEMA_NAME);
            return;
        }
        
        System.out.println("SCHEMA OVERVIEW");
        String dash50 = new String(new char[50]).replace("\0", "-");
        System.out.println(dash50);
        System.out.println("Total tables found: " + tableNames.size());
        System.out.println();
        
        // Print table list
        System.out.println("TABLE LIST:");
        for (int i = 0; i < tableNames.size(); i++) {
            System.out.printf("%2d. %s%n", i + 1, tableNames.get(i));
        }
        System.out.println();
        
        // Extract detailed information for each table
        for (String tableName : tableNames) {
            printTableDetails(tableName);
        }
        
        // Print relationships summary
        printRelationshipsSummary(tableNames);
    }
    
    /**
     * Gets all table names from the specified schema
     */
    private List<String> getAllTables() throws SQLException {
        List<String> tableNames = new ArrayList<>();
        
        String query = "SELECT table_name " +
                      "FROM information_schema.tables " +
                      "WHERE table_schema = ? " +
                      "AND table_type = 'BASE TABLE' " +
                      "ORDER BY table_name";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, SCHEMA_NAME);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                tableNames.add(rs.getString("table_name"));
            }
        }
        
        return tableNames;
    }
    
    /**
     * Prints detailed information for a specific table
     */
    private void printTableDetails(String tableName) throws SQLException {
        String separator = new String(new char[80]).replace("\0", "=");
        System.out.println(separator);
        System.out.println("TABLE: " + tableName.toUpperCase());
        System.out.println(separator);
        
        // Get table comment
        String tableComment = getTableComment(tableName);
        if (tableComment != null && !tableComment.trim().isEmpty()) {
            System.out.println("Description: " + tableComment);
            System.out.println();
        }
        
        // Get columns information
        List<ColumnInfo> columns = getColumnInformation(tableName);
        
        System.out.println("COLUMNS (" + columns.size() + "):");
        String dash120 = new String(new char[120]).replace("\0", "-");
        System.out.println(dash120);
        System.out.printf("%-25s %-20s %-10s %-10s %-15s %-30s%n", 
                         "COLUMN NAME", "DATA TYPE", "NULLABLE", "DEFAULT", "CONSTRAINTS", "DESCRIPTION");
        System.out.println(dash120);
        
        for (ColumnInfo column : columns) {
            System.out.printf("%-25s %-20s %-10s %-10s %-15s %-30s%n",
                             column.name,
                             column.dataType,
                             column.nullable ? "YES" : "NO",
                             column.defaultValue != null ? column.defaultValue : "",
                             column.constraints,
                             column.comment != null ? column.comment : "");
        }
        System.out.println();
        
        // Get indexes
        printIndexes(tableName);
        
        // Get foreign keys
        printForeignKeys(tableName);
        
        // Get referenced by (incoming foreign keys)
        printReferencedBy(tableName);
        
        System.out.println();
    }
    
    /**
     * Gets table comment/description
     */
    private String getTableComment(String tableName) throws SQLException {
        String query = "SELECT obj_description(c.oid) as table_comment " +
                      "FROM pg_class c " +
                      "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                      "WHERE c.relname = ? AND n.nspname = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, tableName);
            stmt.setString(2, SCHEMA_NAME);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("table_comment");
            }
        }
        return null;
    }
    
    /**
     * Gets detailed column information for a table
     */
    private List<ColumnInfo> getColumnInformation(String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        
        String query = "SELECT " +
                      "c.column_name, " +
                      "c.data_type, " +
                      "c.character_maximum_length, " +
                      "c.numeric_precision, " +
                      "c.numeric_scale, " +
                      "c.is_nullable, " +
                      "c.column_default, " +
                      "pgd.description as column_comment, " +
                      "CASE " +
                      "    WHEN pk.column_name IS NOT NULL THEN 'PK' " +
                      "    WHEN fk.column_name IS NOT NULL THEN 'FK' " +
                      "    WHEN c.is_nullable = 'NO' AND pk.column_name IS NULL THEN 'NOT NULL' " +
                      "    ELSE '' " +
                      "END as constraints " +
                      "FROM information_schema.columns c " +
                      "LEFT JOIN pg_catalog.pg_statio_all_tables st ON c.table_schema = st.schemaname AND c.table_name = st.relname " +
                      "LEFT JOIN pg_catalog.pg_description pgd ON pgd.objoid = st.relid AND pgd.objsubid = c.ordinal_position " +
                      "LEFT JOIN ( " +
                      "    SELECT ku.table_name, ku.column_name " +
                      "    FROM information_schema.table_constraints tc " +
                      "    JOIN information_schema.key_column_usage ku ON tc.constraint_name = ku.constraint_name " +
                      "    WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_schema = ? " +
                      ") pk ON c.table_name = pk.table_name AND c.column_name = pk.column_name " +
                      "LEFT JOIN ( " +
                      "    SELECT ku.table_name, ku.column_name " +
                      "    FROM information_schema.table_constraints tc " +
                      "    JOIN information_schema.key_column_usage ku ON tc.constraint_name = ku.constraint_name " +
                      "    WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = ? " +
                      ") fk ON c.table_name = fk.table_name AND c.column_name = fk.column_name " +
                      "WHERE c.table_schema = ? AND c.table_name = ? " +
                      "ORDER BY c.ordinal_position";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, SCHEMA_NAME);
            stmt.setString(2, SCHEMA_NAME);
            stmt.setString(3, SCHEMA_NAME);
            stmt.setString(4, tableName);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                ColumnInfo column = new ColumnInfo();
                column.name = rs.getString("column_name");
                column.dataType = formatDataType(rs);
                column.nullable = "YES".equals(rs.getString("is_nullable"));
                column.defaultValue = rs.getString("column_default");
                column.comment = rs.getString("column_comment");
                column.constraints = rs.getString("constraints");
                
                columns.add(column);
            }
        }
        
        return columns;
    }
    
    /**
     * Formats the data type with length/precision information
     */
    private String formatDataType(ResultSet rs) throws SQLException {
        String dataType = rs.getString("data_type");
        Integer maxLength = rs.getObject("character_maximum_length", Integer.class);
        Integer precision = rs.getObject("numeric_precision", Integer.class);
        Integer scale = rs.getObject("numeric_scale", Integer.class);
        
        if (maxLength != null) {
            return dataType + "(" + maxLength + ")";
        } else if (precision != null && scale != null && scale > 0) {
            return dataType + "(" + precision + "," + scale + ")";
        } else if (precision != null) {
            return dataType + "(" + precision + ")";
        }
        
        return dataType;
    }
    
    /**
     * Prints indexes for a table
     */
    private void printIndexes(String tableName) throws SQLException {
        String query = "SELECT " +
                      "i.relname as index_name, " +
                      "a.attname as column_name, " +
                      "ix.indisunique as is_unique, " +
                      "ix.indisprimary as is_primary " +
                      "FROM pg_class t " +
                      "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                      "JOIN pg_index ix ON t.oid = ix.indrelid " +
                      "JOIN pg_class i ON i.oid = ix.indexrelid " +
                      "JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey) " +
                      "WHERE t.relname = ? AND n.nspname = ? " +
                      "ORDER BY i.relname, a.attnum";
        
        Map<String, List<String>> indexes = new HashMap<>();
        Map<String, String> indexTypes = new HashMap<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, tableName);
            stmt.setString(2, SCHEMA_NAME);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String indexName = rs.getString("index_name");
                String columnName = rs.getString("column_name");
                boolean isUnique = rs.getBoolean("is_unique");
                boolean isPrimary = rs.getBoolean("is_primary");
                
                indexes.computeIfAbsent(indexName, k -> new ArrayList<>()).add(columnName);
                
                String type = isPrimary ? "PRIMARY KEY" : (isUnique ? "UNIQUE" : "INDEX");
                indexTypes.put(indexName, type);
            }
        }
        
        if (!indexes.isEmpty()) {
            System.out.println("INDEXES:");
            String dash80 = new String(new char[80]).replace("\0", "-");
            System.out.println(dash80);
            for (Map.Entry<String, List<String>> entry : indexes.entrySet()) {
                String indexName = entry.getKey();
                List<String> columns = entry.getValue();
                String type = indexTypes.get(indexName);
                
                System.out.printf("%-30s %-15s %s%n", indexName, type, String.join(", ", columns));
            }
            System.out.println();
        }
    }
    
    /**
     * Prints foreign key relationships for a table
     */
    private void printForeignKeys(String tableName) throws SQLException {
        String query = "SELECT " +
                      "tc.constraint_name, " +
                      "kcu.column_name, " +
                      "ccu.table_name AS foreign_table_name, " +
                      "ccu.column_name AS foreign_column_name " +
                      "FROM information_schema.table_constraints tc " +
                      "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                      "JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name " +
                      "WHERE tc.constraint_type = 'FOREIGN KEY' " +
                      "AND tc.table_schema = ? " +
                      "AND tc.table_name = ? " +
                      "ORDER BY tc.constraint_name";
        
        List<ForeignKeyInfo> foreignKeys = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, SCHEMA_NAME);
            stmt.setString(2, tableName);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                ForeignKeyInfo fk = new ForeignKeyInfo();
                fk.constraintName = rs.getString("constraint_name");
                fk.columnName = rs.getString("column_name");
                fk.referencedTable = rs.getString("foreign_table_name");
                fk.referencedColumn = rs.getString("foreign_column_name");
                foreignKeys.add(fk);
            }
        }
        
        if (!foreignKeys.isEmpty()) {
            System.out.println("FOREIGN KEYS (REFERENCES):");
            String dash80 = new String(new char[80]).replace("\0", "-");
            System.out.println(dash80);
            for (ForeignKeyInfo fk : foreignKeys) {
                System.out.printf("%-25s -> %s.%s%n", 
                                 fk.columnName, fk.referencedTable, fk.referencedColumn);
            }
            System.out.println();
        }
    }
    
    /**
     * Prints tables that reference this table (incoming foreign keys)
     */
    private void printReferencedBy(String tableName) throws SQLException {
        String query = "SELECT " +
                      "tc.table_name as referencing_table, " +
                      "kcu.column_name as referencing_column, " +
                      "ccu.column_name as referenced_column " +
                      "FROM information_schema.table_constraints tc " +
                      "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                      "JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name " +
                      "WHERE tc.constraint_type = 'FOREIGN KEY' " +
                      "AND tc.table_schema = ? " +
                      "AND ccu.table_name = ? " +
                      "ORDER BY tc.table_name";
        
        List<String> referencedBy = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, SCHEMA_NAME);
            stmt.setString(2, tableName);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String referencingTable = rs.getString("referencing_table");
                String referencingColumn = rs.getString("referencing_column");
                String referencedColumn = rs.getString("referenced_column");
                
                referencedBy.add(String.format("%s.%s -> %s", 
                               referencingTable, referencingColumn, referencedColumn));
            }
        }
        
        if (!referencedBy.isEmpty()) {
            System.out.println("REFERENCED BY:");
            String dash80 = new String(new char[80]).replace("\0", "-");
            System.out.println(dash80);
            for (String reference : referencedBy) {
                System.out.println(reference);
            }
            System.out.println();
        }
    }
    
    /**
     * Prints a summary of all relationships in the schema
     */
    private void printRelationshipsSummary(List<String> tableNames) throws SQLException {
        String separator = new String(new char[80]).replace("\0", "=");
        System.out.println(separator);
        System.out.println("RELATIONSHIPS SUMMARY");
        System.out.println(separator);
        
        String query = "SELECT " +
                      "tc.table_name as from_table, " +
                      "kcu.column_name as from_column, " +
                      "ccu.table_name as to_table, " +
                      "ccu.column_name as to_column " +
                      "FROM information_schema.table_constraints tc " +
                      "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                      "JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name " +
                      "WHERE tc.constraint_type = 'FOREIGN KEY' " +
                      "AND tc.table_schema = ? " +
                      "ORDER BY tc.table_name, ccu.table_name";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, SCHEMA_NAME);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String fromTable = rs.getString("from_table");
                String fromColumn = rs.getString("from_column");
                String toTable = rs.getString("to_table");
                String toColumn = rs.getString("to_column");
                
                System.out.printf("%s.%s -> %s.%s%n", fromTable, fromColumn, toTable, toColumn);
            }
        }
        
        System.out.println();
        System.out.println("Schema extraction completed successfully!");
    }
    
    // Helper classes
    private static class ColumnInfo {
        String name;
        String dataType;
        boolean nullable;
        String defaultValue;
        String comment;
        String constraints;
    }
    
    private static class ForeignKeyInfo {
        String constraintName;
        String columnName;
        String referencedTable;
        String referencedColumn;
    }
}