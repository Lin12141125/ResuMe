package com.iras;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "iras.test.ai", matches = "true")
class AiIntegrationTest {
    static AiService service() throws Exception {
        var config = DatabaseMigrationTest.configuration();
        var environment = new MockEnvironment();
        environment.setProperty("iras.chat.base-url", config.get("OPENAI_BASE_URL"));
        environment.setProperty("iras.chat.api-key", config.get("OPENAI_API_KEY"));
        environment.setProperty("iras.chat.model", config.get("OPENAI_MODEL"));
        environment.setProperty("iras.embedding.base-url", config.get("EMBEDDING_BASE_URL"));
        environment.setProperty("iras.embedding.api-key", config.get("EMBEDDING_API_KEY"));
        environment.setProperty("iras.embedding.model", config.get("EMBEDDING_MODEL"));
        return new AiService(environment, new ObjectMapper());
    }

    @Test
    void realJavaClientsReturnJsonAndEmbedding() throws Exception {
        var service = service();
        var result = service.structured("Extract name and skills (array of strings).",
                "Synthetic candidate: Example Candidate. Skills: Java, PostgreSQL.");
        assertThat(result.path("skills").isArray()).isTrue();
        assertThat(result.path("skills").toString()).contains("Java");
        assertThat(service.embed("Synthetic Java backend experience")).hasSize(1024);
    }
}