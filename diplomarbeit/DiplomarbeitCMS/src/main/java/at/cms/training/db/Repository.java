package at.cms.training.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;
import java.util.logging.Level;

public class Repository {
    private static final Logger log = Logger.getLogger(Repository.class.getName());
    private static final String DB_URL = "jdbc:sqlite:documents.db";
    private static Connection connection;

    public static void connect() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            initializeTables();
            log.info("Database connection established");
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Could not connect to database", e);
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private static void initializeTables() throws SQLException {
        String createDocumentsTable = """
            CREATE TABLE IF NOT EXISTS documents (
                id TEXT PRIMARY KEY,
                title TEXT,
                content TEXT,
                source TEXT,
                author TEXT,
                created_at TIMESTAMP,
                category TEXT,
                tags TEXT,
                language TEXT,
                word_count INTEGER,
                embedding_model TEXT,
                last_updated TIMESTAMP
            )
        """;

        String createChunksTable = """
            CREATE TABLE IF NOT EXISTS document_chunks (
                chunk_id TEXT PRIMARY KEY,
                document_id TEXT,
                chunk_index INTEGER,
                chunk_text TEXT,
                start_position INTEGER,
                end_position INTEGER,
                FOREIGN KEY (document_id) REFERENCES documents(id)
            )
        """;

        String createQueryHistoryTable = """
            CREATE TABLE IF NOT EXISTS query_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_query TEXT,
                retrieved_documents TEXT,
                llm_response TEXT,
                timestamp TIMESTAMP,
                response_rating INTEGER
            )
        """;

        String createDocumentValidationTable = """
            CREATE TABLE IF NOT EXISTS documentValidation (id INTEGER PRIMARY KEY AUTOINCREMENT, file_Hash TEXT, file_Name Text)
        """;

        try (var statement = connection.createStatement()) {
            statement.execute(createDocumentsTable);
            statement.execute(createChunksTable);
            statement.execute(createQueryHistoryTable);
            statement.execute(createDocumentValidationTable);
        }
    }

    public static Connection getConnection() {
        return connection;
    }
}

