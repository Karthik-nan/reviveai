package com.reviveai.rag.service;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class PolicyLoaderService {

    private final PolicyRagService policyRagService;

    public PolicyLoaderService(PolicyRagService policyRagService) {
        this.policyRagService = policyRagService;
    }

    public void loadPolicies() throws IOException {

        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        Resource[] resources =
                resolver.getResources("classpath:/policies/*.txt");

        List<Document> documents = new ArrayList<>();

        for (Resource resource : resources) {

            String content = new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            Document document = new Document(content);

            document.getMetadata().put(
                    "source",
                    resource.getFilename()
            );

            documents.add(document);
        }

        System.out.println(
                "Found " + documents.size() + " policy documents."
        );

        policyRagService.ingestPolicies(documents);
    }
}

