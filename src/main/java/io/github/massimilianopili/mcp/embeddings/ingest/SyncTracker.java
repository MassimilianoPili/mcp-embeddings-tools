package io.github.massimilianopili.mcp.embeddings.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Traccia quali file sono stati indicizzati e quando.
 * Permette indicizzazione incrementale (solo file nuovi o modificati).
 */
@Component
public class SyncTracker {

    private static final Logger log = LoggerFactory.getLogger(SyncTracker.class);
    private final JdbcTemplate jdbc;

    public SyncTracker(@Qualifier("embeddingsJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS embeddings_sync (
                    file_path TEXT PRIMARY KEY,
                    last_modified TIMESTAMPTZ NOT NULL,
                    chunk_count INTEGER NOT NULL,
                    indexed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )""");
    }

    /**
     * Controlla se un file necessita re-indicizzazione.
     * Ritorna true se il file e' nuovo o se last_modified e' cambiato.
     */
    public boolean needsReindex(Path file) {
        try {
            Instant fileModified = Files.getLastModifiedTime(file).toInstant();
            List<Timestamp> rows = jdbc.query(
                    "SELECT last_modified FROM embeddings_sync WHERE file_path = ?",
                    (rs, i) -> rs.getTimestamp("last_modified"),
                    file.toString());

            if (rows.isEmpty()) return true;
            return fileModified.isAfter(rows.get(0).toInstant());
        } catch (Exception e) {
            log.warn("Errore check sync {}: {}", file, e.getMessage());
            return true;
        }
    }

    /**
     * Aggiorna il tracking dopo indicizzazione riuscita.
     */
    public void markIndexed(Path file, int chunkCount) {
        try {
            Instant fileModified = Files.getLastModifiedTime(file).toInstant();
            jdbc.update("""
                    INSERT INTO embeddings_sync (file_path, last_modified, chunk_count, indexed_at)
                    VALUES (?, ?, ?, NOW())
                    ON CONFLICT (file_path) DO UPDATE
                    SET last_modified = EXCLUDED.last_modified,
                        chunk_count = EXCLUDED.chunk_count,
                        indexed_at = NOW()""",
                    file.toString(), Timestamp.from(fileModified), chunkCount);
        } catch (Exception e) {
            log.error("Errore aggiornamento sync {}: {}", file, e.getMessage());
        }
    }

    /**
     * Rimuove il tracking per un file cancellato.
     */
    public void removeTracking(String filePath) {
        jdbc.update("DELETE FROM embeddings_sync WHERE file_path = ?", filePath);
    }

    /**
     * Ritorna tutti i file tracciati per un dato tipo (basato su estensione).
     */
    public Set<String> getTrackedFiles(String extension) {
        String pattern = "%." + extension;
        return new HashSet<>(jdbc.query(
                "SELECT file_path FROM embeddings_sync WHERE file_path LIKE ?",
                (rs, i) -> rs.getString("file_path"),
                pattern));
    }

    /**
     * Statistiche aggregate.
     */
    public List<java.util.Map<String, Object>> getStats() {
        return jdbc.queryForList("""
                SELECT
                    CASE
                        WHEN file_path LIKE '%.jsonl' THEN 'conversation'
                        WHEN file_path LIKE '%.md' THEN 'docs'
                        ELSE 'other'
                    END AS type,
                    COUNT(*) AS file_count,
                    SUM(chunk_count) AS total_chunks,
                    MAX(indexed_at) AS last_indexed
                FROM embeddings_sync
                GROUP BY 1
                ORDER BY 1""");
    }
}
