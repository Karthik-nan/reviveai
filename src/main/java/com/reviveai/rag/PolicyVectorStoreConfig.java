package com.reviveai.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PolicyVectorStoreConfig {

    @Bean
    public VectorStore policyVectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel) {

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("policy_vectors")
                .build();
    }
}