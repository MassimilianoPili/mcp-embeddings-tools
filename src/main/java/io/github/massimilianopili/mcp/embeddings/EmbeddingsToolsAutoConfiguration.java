package io.github.massimilianopili.mcp.embeddings;

import io.github.massimilianopili.mcp.embeddings.ingest.ChunkingService;
import io.github.massimilianopili.mcp.embeddings.ingest.ConversationParser;
import io.github.massimilianopili.mcp.embeddings.ingest.MarkdownParser;
import io.github.massimilianopili.mcp.embeddings.ingest.SyncTracker;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(name = "mcp.embeddings.enabled", havingValue = "true")
@Import({EmbeddingsConfig.class, EmbeddingsTools.class,
         ChunkingService.class, ConversationParser.class, MarkdownParser.class, SyncTracker.class})
public class EmbeddingsToolsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "embeddingsToolCallbackProvider")
    public ToolCallbackProvider embeddingsToolCallbackProvider(EmbeddingsTools embeddingsTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(embeddingsTools)
                .build();
    }
}
