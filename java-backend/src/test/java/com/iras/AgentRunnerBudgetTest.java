package com.iras;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunnerBudgetTest {
    private static final String REQUIREMENTS = "{\"mustHave\":[\"Java\"],\"preferred\":[]}";
    private final ObjectMapper json = new ObjectMapper();

    private AgentRunner runner(AiService ai, SearchService search, int rounds, int calls) {
        var environment = new MockEnvironment()
                .withProperty("iras.agent.max-rounds", String.valueOf(rounds))
                .withProperty("iras.agent.max-tool-calls", String.valueOf(calls));
        return new AgentRunner(ai, search, mock(JdbcTemplate.class), json, environment);
    }

    private ChatResponse chatResponse(AssistantMessage output) {
        var metadata = ChatResponseMetadata.builder().model("synthetic").usage(new EmptyUsage()).build();
        return new ChatResponse(List.of(new Generation(output)), metadata);
    }

    private ToolCall toolCall(String name, String arguments) {
        return new AssistantMessage.ToolCall("tool-" + name, "function", name, arguments);
    }

    private AiService aiReturning(AssistantMessage output) {
        var model = mock(OpenAiChatModel.class);
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponse(output));
        var ai = mock(AiService.class);
        when(ai.chatModel()).thenReturn(model);
        return ai;
    }

    @Test
    void roundBudgetIsExhaustedWhenModelNeverUsesTools() throws Exception {
        var output = mock(AssistantMessage.class);
        when(output.hasToolCalls()).thenReturn(false);
        when(output.getText()).thenReturn("Plain text without tools.");
        var runner = runner(aiReturning(output), mock(SearchService.class), 1, 8);
        assertThatThrownBy(() -> runner.run(1L, json.readTree(REQUIREMENTS), "Synthetic request", () -> { }, event -> { }))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var api = (ApiException) error;
                    org.assertj.core.api.Assertions.assertThat(api.status()).isEqualTo(422);
                    org.assertj.core.api.Assertions.assertThat(api.getMessage()).contains("round budget");
                });
    }

    @Test
    void unauthorizedToolIsRejectedWithoutExecution() throws Exception {
        var output = mock(AssistantMessage.class);
        when(output.hasToolCalls()).thenReturn(true);
        when(output.getToolCalls()).thenReturn(List.of(toolCall("deleteAllResumes", "{}")));
        var runner = runner(aiReturning(output), mock(SearchService.class), 5, 8);
        assertThatThrownBy(() -> runner.run(1L, json.readTree(REQUIREMENTS), "Synthetic request", () -> { }, event -> { }))
                .isInstanceOf(ApiException.class)
                .hasMessage("Agent requested an unauthorized tool");
    }

    @Test
    void toolBudgetIsExhaustedAfterConfiguredLimit() throws Exception {
        var output = mock(AssistantMessage.class);
        when(output.hasToolCalls()).thenReturn(true);
        when(output.getToolCalls()).thenReturn(List.of(toolCall("searchResumes", "{\"query\":\"java\"}")));
        var search = mock(SearchService.class);
        when(search.search(1L, "java", null)).thenReturn(List.of());
        var runner = runner(aiReturning(output), search, 5, 1);
        assertThatThrownBy(() -> runner.run(1L, json.readTree(REQUIREMENTS), "Synthetic request", () -> { }, event -> { }))
                .isInstanceOf(ApiException.class)
                .hasMessage("Agent tool budget exhausted");
    }
}
