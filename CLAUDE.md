# MCP Embeddings Tools

Spring Boot starter per ricerca semantica vettoriale via MCP. Usa pgvector (PostgreSQL) per lo storage dei vettori e ONNX all-MiniLM-L6-v2 per generare embedding in-process (384 dimensioni, zero API esterne).

Pubblicato su Maven Central: `io.github.massimilianopili:mcp-embeddings-tools`

## Comandi Build

```bash
# Build
/opt/maven/bin/mvn clean compile

# Install locale (skip GPG)
/opt/maven/bin/mvn clean install -Dgpg.skip=true

# Deploy su Maven Central (richiede GPG + credenziali Central Portal in ~/.m2/settings.xml)
/opt/maven/bin/mvn clean deploy
```

Java 17+. Maven in `/opt/maven/bin/mvn`.

## Struttura Progetto

```
src/main/java/io/github/massimilianopili/mcp/embeddings/
├── EmbeddingsConfig.java                  # DataSource dedicato + PgVectorStore + ONNX model (384 dim)
├── EmbeddingsProperties.java              # @ConfigurationProperties prefix "mcp.embeddings"
├── EmbeddingsTools.java                   # 5 @Tool MCP (search, search_conversations, search_docs, stats, reindex)
├── EmbeddingsToolsAutoConfiguration.java  # Spring Boot auto-config (@Import di tutti i bean)
└── ingest/
    ├── ChunkingService.java               # Orchestratore pipeline: scansione file, parser, batch add
    ├── ConversationParser.java            # Parser .jsonl Claude Code (turni user+assistant, skip thinking/tool_use)
    ├── MarkdownParser.java                # Parser .md (split per heading ##/###, overlap 200 char)
    └── SyncTracker.java                   # Indicizzazione incrementale (tabella embeddings_sync)

src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## Pattern Chiave

- **Auto-configuration**: si attiva con `mcp.embeddings.enabled=true`. Se disabilitato, zero impatto.
- **DataSource dedicato**: HikariCP separato (pool 3 conn, nome `embeddings-pool`) — non conflittua con il DataSource principale dell'app.
- **ONNX inline**: il modello e' creato inline nel bean VectorStore (non come bean separato) per evitare ORT_FAIL da istanze multiple del runtime ONNX. Richiede `afterPropertiesSet()` esplicito.
- **Indicizzazione incrementale**: `SyncTracker` confronta `last_modified` del file con il timestamp in `embeddings_sync` — solo file nuovi o modificati.
- **Chunking conversazioni**: per turno (domanda user + risposta assistant text). Skip: thinking, tool_use, tool_result, queue-operation, file-history-snapshot.
- **Chunking docs**: per heading (##/###), max 2000 char con overlap 200.

## Configurazione

```properties
mcp.embeddings.enabled=${MCP_EMBEDDINGS_ENABLED:false}
mcp.embeddings.db-url=${MCP_EMBEDDINGS_DB_URL:jdbc:postgresql://localhost:5432/embeddings}
mcp.embeddings.db-username=${MCP_EMBEDDINGS_DB_USER:postgres}
mcp.embeddings.db-credential=${MCP_EMBEDDINGS_DB_CREDENTIAL:}
mcp.embeddings.conversations-path=${MCP_EMBEDDINGS_CONVERSATIONS_PATH:}
mcp.embeddings.docs-path=${MCP_EMBEDDINGS_DOCS_PATH:}
mcp.embeddings.model-cache-dir=${MCP_EMBEDDINGS_MODEL_CACHE:/tmp/onnx-models}
```

## Dipendenze

- Spring Boot 3.4.1, Spring AI 1.0.0
- `spring-ai-pgvector-store` — PgVectorStore (COSINE_DISTANCE, HNSW index)
- `spring-ai-transformers` — ONNX embedding model in-process
- PostgreSQL 42.7.4 (driver, provided)
- Jackson 2.18.2 (parsing JSONL, provided)
