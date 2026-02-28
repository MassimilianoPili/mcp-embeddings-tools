package io.github.massimilianopili.mcp.embeddings;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "mcp.embeddings.enabled", havingValue = "true")
@EnableConfigurationProperties(EmbeddingsProperties.class)
public class EmbeddingsConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingsConfig.class);

    @Bean("embeddingsDataSource")
    public DataSource embeddingsDataSource(EmbeddingsProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(props.getDbUrl());
        ds.setUsername(props.getDbUsername());
        ds.setPassword(props.getDbCredential());
        ds.setMaximumPoolSize(3);
        ds.setPoolName("embeddings-pool");
        log.info("Embeddings DataSource: {}", props.getDbUrl());
        return ds;
    }

    @Bean("embeddingsVectorStore")
    public VectorStore embeddingsVectorStore(
            @Qualifier("embeddingsDataSource") DataSource dataSource,
            EmbeddingsProperties props) {
        // Creo il modello ONNX inline per evitare istanze multiple
        // (ONNX Runtime ha un logger globale singleton — multipli bean causano ORT_FAIL)
        TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
        if (props.getModelCacheDir() != null && !props.getModelCacheDir().isBlank()) {
            embeddingModel.setResourceCacheDirectory(props.getModelCacheDir());
            log.info("Embedding model cache: {}", props.getModelCacheDir());
        }
        log.info("Embedding model: all-MiniLM-L6-v2 (384 dim, ONNX in-process)");
        try {
            embeddingModel.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("Inizializzazione modello ONNX fallita", e);
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        VectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(384)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
        log.info("PgVectorStore: 384 dim, COSINE_DISTANCE, HNSW index");
        return store;
    }

    @Bean("embeddingsJdbcTemplate")
    public JdbcTemplate embeddingsJdbcTemplate(
            @Qualifier("embeddingsDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
