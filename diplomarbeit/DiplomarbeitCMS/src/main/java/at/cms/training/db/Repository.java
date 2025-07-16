package at.cms.training.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import java.util.logging.Level;

public class Repository {
    private static final Logger log = Logger.getLogger(Repository.class.getName());
    private static final String DB_URL = "jdbc:sqlite:documents.db";

    public static void connect() {
        try(Connection conn = DriverManager.getConnection(DB_URL)) {
            if (conn != null) {
                System.out.println("Connected to the database.");
                initializeTables();
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to connect to the database", e);
            throw new RuntimeException("Database connection error", e);
        }
    }

    private static void initializeTables() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL);
                Statement stmt = conn.createStatement()) {
            
            // warum auch immer weil ich es nicht hinbekomme, dass die foreign keys funktionieren muss on sein damit cascaded wird 
            stmt.execute("PRAGMA foreign_keys = ON;");
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    content BLOB NOT NULL,
                    source TEXT UNIQUE NOT NULL,
                    author TEXT,
                    created_at TIMESTAMP,
                    language TEXT,
                    word_count INTEGER,
                    last_updated TIMESTAMP
                )
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS chunks (
                    id TEXT PRIMARY KEY,
                    document_id TEXT NOT NULL,
                    content TEXT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    vectorized BOOLEAN DEFAULT FALSE,
                    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
                )
            """);
            
            // Add vectorized column to existing chunks table if it doesn't exist
            try {
                stmt.execute("ALTER TABLE chunks ADD COLUMN vectorized BOOLEAN DEFAULT FALSE");
                log.info("Added vectorized column to chunks table");
            } catch (SQLException e) {
                // Column already exists, this is expected
                log.fine("Vectorized column already exists in chunks table");
            }
        }
    }

    public static Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(DB_URL);
            if (conn != null) {
                // Enable foreign keys for this connection
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                }
                conn.setAutoCommit(false);
                return conn;
            } else {
                throw new SQLException("Failed to establish a connection to the database.");
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to get database connection", e);
            throw new RuntimeException("Database connection error", e);
        }
    }
}

