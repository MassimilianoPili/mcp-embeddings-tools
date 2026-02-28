package io.github.massimilianopili.mcp.embeddings.ingest;

import io.github.massimilianopili.mcp.embeddings.EmbeddingsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestratore della pipeline di ingestion.
 * Scansiona file, invoca i parser appropriati, e aggiorna il vector store.
 * Supporta indicizzazione incrementale via SyncTracker.
 */
@Component
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;
    private final ConversationParser conversationParser;
    private final MarkdownParser markdownParser;
    private final SyncTracker syncTracker;
    private final EmbeddingsProperties properties;

    public ChunkingService(
            @Qualifier("embeddingsVectorStore") VectorStore vectorStore,
            @Qualifier("embeddingsJdbcTemplate") JdbcTemplate jdbc,
            ConversationParser conversationParser,
            MarkdownParser markdownParser,
            SyncTracker syncTracker,
            EmbeddingsProperties properties) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.conversationParser = conversationParser;
        this.markdownParser = markdownParser;
        this.syncTracker = syncTracker;
        this.properties = properties;
    }

    /**
     * Re-indicizza le conversazioni Claude (.jsonl).
     * Ritorna statistiche sull'operazione.
     */
    public Map<String, Object> reindexConversations() {
        String basePath = properties.getConversationsPath();
        if (basePath == null || basePath.isBlank()) {
            return Map.of("error", "mcp.embeddings.conversations-path non configurato");
        }

        int filesProcessed = 0;
        int filesSkipped = 0;
        int totalChunks = 0;
        List<String> errors = new ArrayList<>();

        try {
            List<Path> jsonlFiles = findFiles(Path.of(basePath), "*.jsonl");
            Set<String> trackedFiles = syncTracker.getTrackedFiles("jsonl");

            for (Path file : jsonlFiles) {
                if (!syncTracker.needsReindex(file)) {
                    filesSkipped++;
                    trackedFiles.remove(file.toString());
                    continue;
                }

                try {
                    // Rimuovi vecchi embedding di questo file
                    removeDocumentsForFile(file.toString());

                    List<Document> docs = conversationParser.parse(file);
                    if (!docs.isEmpty()) {
                        // Indicizza in batch da 50
                        for (int i = 0; i < docs.size(); i += 50) {
                            int end = Math.min(i + 50, docs.size());
                            vectorStore.add(docs.subList(i, end));
                        }
                        syncTracker.markIndexed(file, docs.size());
                        totalChunks += docs.size();
                    }
                    filesProcessed++;
                    trackedFiles.remove(file.toString());
                } catch (Exception e) {
                    log.error("Errore indicizzazione {}: {}", file, e.getMessage());
                    errors.add(file.getFileName().toString() + ": " + e.getMessage());
                }
            }

            // Rimuovi tracking per file cancellati
            for (String deletedFile : trackedFiles) {
                removeDocumentsForFile(deletedFile);
                syncTracker.removeTracking(deletedFile);
            }

        } catch (Exception e) {
            return Map.of("error", "Errore scansione: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", "conversation");
        result.put("files_processed", filesProcessed);
        result.put("files_skipped", filesSkipped);
        result.put("total_chunks", totalChunks);
        if (!errors.isEmpty()) result.put("errors", errors);
        return result;
    }

    /**
     * Re-indicizza la documentazione markdown (.md).
     * Ritorna statistiche sull'operazione.
     */
    public Map<String, Object> reindexDocs() {
        String basePath = properties.getDocsPath();
        if (basePath == null || basePath.isBlank()) {
            return Map.of("error", "mcp.embeddings.docs-path non configurato");
        }

        int filesProcessed = 0;
        int filesSkipped = 0;
        int totalChunks = 0;
        List<String> errors = new ArrayList<>();

        try {
            List<Path> mdFiles = findMarkdownFiles(Path.of(basePath));
            Set<String> trackedFiles = syncTracker.getTrackedFiles("md");

            for (Path file : mdFiles) {
                if (!syncTracker.needsReindex(file)) {
                    filesSkipped++;
                    trackedFiles.remove(file.toString());
                    continue;
                }

                try {
                    removeDocumentsForFile(file.toString());

                    List<Document> docs = markdownParser.parse(file);
                    if (!docs.isEmpty()) {
                        for (int i = 0; i < docs.size(); i += 50) {
                            int end = Math.min(i + 50, docs.size());
                            vectorStore.add(docs.subList(i, end));
                        }
                        syncTracker.markIndexed(file, docs.size());
                        totalChunks += docs.size();
                    }
                    filesProcessed++;
                    trackedFiles.remove(file.toString());
                } catch (Exception e) {
                    log.error("Errore indicizzazione {}: {}", file, e.getMessage());
                    errors.add(file.getFileName().toString() + ": " + e.getMessage());
                }
            }

            for (String deletedFile : trackedFiles) {
                removeDocumentsForFile(deletedFile);
                syncTracker.removeTracking(deletedFile);
            }

        } catch (Exception e) {
            return Map.of("error", "Errore scansione: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", "docs");
        result.put("files_processed", filesProcessed);
        result.put("files_skipped", filesSkipped);
        result.put("total_chunks", totalChunks);
        if (!errors.isEmpty()) result.put("errors", errors);
        return result;
    }

    /**
     * Trova file .jsonl ricorsivamente nelle sottodirectory del path base.
     */
    private List<Path> findFiles(Path basePath, String glob) throws IOException {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(basePath)) return result;

        // Cerca ricorsivamente nelle sottodirectory (projects hanno sottocartelle per progetto)
        Files.walk(basePath)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(
                        glob.replace("*", "")))
                .forEach(result::add);
        return result;
    }

    /**
     * Trova file .md nelle posizioni rilevanti (CLAUDE.md, README.md, docs/, Vari/CLAUDE.md).
     */
    private List<Path> findMarkdownFiles(Path basePath) throws IOException {
        List<Path> result = new ArrayList<>();

        // File principali
        addIfExists(result, basePath.resolve("CLAUDE.md"));
        addIfExists(result, basePath.resolve("README.md"));

        // Directory docs/
        Path docsDir = basePath.resolve("docs");
        if (Files.isDirectory(docsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(docsDir, "*.md")) {
                stream.forEach(result::add);
            }
        }

        // CLAUDE.md nelle sottodirectory di Vari/
        Path variDir = basePath.resolve("Vari");
        if (Files.isDirectory(variDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(variDir)) {
                for (Path subDir : stream) {
                    if (Files.isDirectory(subDir)) {
                        addIfExists(result, subDir.resolve("CLAUDE.md"));
                    }
                }
            }
        }

        // Memory file
        Path memoryFile = Path.of(System.getProperty("user.home"),
                ".claude/projects/-data-massimiliano/memory/MEMORY.md");
        addIfExists(result, memoryFile);

        return result;
    }

    private void addIfExists(List<Path> list, Path file) {
        if (Files.isRegularFile(file)) list.add(file);
    }

    /**
     * Rimuove tutti i documenti del vector store associati a un file sorgente.
     * Query SQL diretta sulla tabella vector_store di pgvector (metadata JSONB).
     */
    private void removeDocumentsForFile(String filePath) {
        try {
            int deleted = jdbc.update(
                    "DELETE FROM vector_store WHERE metadata->>'source_file' = ?", filePath);
            if (deleted > 0) {
                log.debug("Rimossi {} vecchi embedding per {}", deleted, filePath);
            }
        } catch (Exception e) {
            log.warn("Errore rimozione embedding per {}: {}", filePath, e.getMessage());
        }
    }
}
