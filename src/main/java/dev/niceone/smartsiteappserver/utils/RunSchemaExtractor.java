package dev.niceone.smartsiteappserver.utils;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;

/**
 * Wrapper to run PostgreSQL Schema Extractor and save output to file
 */
public class RunSchemaExtractor {
    public static void main(String[] args) {
        try {
            // Redirect output to file
            PrintStream fileOut = new PrintStream(new FileOutputStream("database_schema_report.txt"));
            PrintStream originalOut = System.out;
            System.setOut(fileOut);
            
            // Run the schema extractor
            PostgreSQLSchemaExtractor.main(args);
            
            // Close file and restore original output
            fileOut.close();
            System.setOut(originalOut);
            
            System.out.println("Schema report has been saved to: database_schema_report.txt");
            
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
}