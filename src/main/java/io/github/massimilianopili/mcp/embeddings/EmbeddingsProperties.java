package io.github.massimilianopili.mcp.embeddings;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proprieta' di configurazione per mcp-embeddings-tools.
 * I valori reali vengono da variabili d'ambiente o application.properties.
 */
@ConfigurationProperties(prefix = "mcp.embeddings")
public class EmbeddingsProperties {

    private boolean enabled;
    private String dbUrl = "jdbc:postgresql://localhost:5432/embeddings";
    private String dbUsername = "postgres";
    /** Impostare via env var MCP_EMBEDDINGS_DB_PASSWORD */
    private String dbCredential;
    private String conversationsPath;
    private String docsPath;
    private String modelCacheDir;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDbUrl() { return dbUrl; }
    public void setDbUrl(String dbUrl) { this.dbUrl = dbUrl; }

    public String getDbUsername() { return dbUsername; }
    public void setDbUsername(String dbUsername) { this.dbUsername = dbUsername; }

    public String getDbCredential() { return dbCredential; }
    public void setDbCredential(String dbCredential) { this.dbCredential = dbCredential; }

    public String getConversationsPath() { return conversationsPath; }
    public void setConversationsPath(String conversationsPath) { this.conversationsPath = conversationsPath; }

    public String getDocsPath() { return docsPath; }
    public void setDocsPath(String docsPath) { this.docsPath = docsPath; }

    public String getModelCacheDir() { return modelCacheDir; }
    public void setModelCacheDir(String modelCacheDir) { this.modelCacheDir = modelCacheDir; }
}
