package com.reviveai.rag.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PolicyRagService {

    private final VectorStore vectorStore;

    public PolicyRagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ingestPolicies(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        vectorStore.add(documents);
    }

    public List<Document> searchPolicies(String query, int topK) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        return vectorStore.similaritySearch(query)
                .stream()
                .limit(topK)
                .toList();
    }
}

