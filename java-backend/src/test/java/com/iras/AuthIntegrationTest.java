package com.iras;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {
    static final String SCHEMA = "iras_test_" + UUID.randomUUID().toString().replace("-", "");
    static final java.nio.file.Path STORAGE = java.nio.file.Path.of("target", SCHEMA);
    static final Map<String, String> CONFIG;
    static {
        try { CONFIG = DatabaseMigrationTest.configuration(); }
        catch (Exception error) { throw new IllegalStateException("Real database configuration required", error); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("iras.auth.requests-per-minute", () -> 500);
        registry.add("spring.datasource.url", () -> CONFIG.get("DATABASE_URL")
                + (CONFIG.get("DATABASE_URL").contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public");
        registry.add("spring.datasource.username", () -> CONFIG.get("DATABASE_USER"));
        registry.add("spring.datasource.password", () -> CONFIG.get("DATABASE_PASSWORD"));
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("iras.storage", () -> STORAGE.toString());
        registry.add("iras.chat.base-url", () -> CONFIG.getOrDefault("OPENAI_BASE_URL", ""));
        registry.add("iras.chat.api-key", () -> CONFIG.getOrDefault("OPENAI_API_KEY", ""));
        registry.add("iras.chat.model", () -> CONFIG.getOrDefault("OPENAI_MODEL", ""));
        registry.add("iras.embedding.base-url", () -> CONFIG.getOrDefault("EMBEDDING_BASE_URL", ""));
        registry.add("iras.embedding.api-key", () -> CONFIG.getOrDefault("EMBEDDING_API_KEY", ""));
        registry.add("iras.embedding.model", () -> CONFIG.getOrDefault("EMBEDDING_MODEL", ""));
    }

    @AfterAll
    static void cleanup(@Autowired org.springframework.context.ApplicationContext context) throws Exception {
        context.getBeansOfType(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class).values().forEach(scheduler -> scheduler.shutdown());
        try (var connection = java.sql.DriverManager.getConnection(CONFIG.get("DATABASE_URL"),
                CONFIG.get("DATABASE_USER"), CONFIG.get("DATABASE_PASSWORD")); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
        if (java.nio.file.Files.exists(STORAGE)) {
            try (var paths = java.nio.file.Files.walk(STORAGE)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(path);
            }
        }
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;
    @Autowired SearchService search;
    @Autowired AgentRunner agent;
    @Autowired FileCleanup cleanupFiles;

    HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    HttpResponse<String> request(HttpClient client, String method, String path, Object body, String csrf) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    String csrf(HttpClient client) throws Exception {
        return json.readTree(request(client, "GET", "/api/auth/csrf", null, null).body()).get("token").asText();
    }

    HttpClient register() throws Exception {
        var browser = client();
        var response = request(browser, "POST", "/api/auth/register", Map.of(
                "account", "synthetic-" + UUID.randomUUID(), "password", "Synthetic-Password-2026"), csrf(browser));
        assertThat(response.statusCode()).isEqualTo(200);
        return browser;
    }

    HttpResponse<String> upload(HttpClient browser) throws Exception {
        String boundary = "iras-synthetic-boundary";
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"synthetic.txt\"\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n\r\n"
                + "姓名: Synthetic Candidate\n技能专长: Java, PostgreSQL\n工作经历: 2020-2024 Example Ltd, Java backend engineer.\n"
                + "Built Spring Boot REST APIs and PostgreSQL transaction processing. Synthetic testing data only.\r\n"
                + "--" + boundary + "--\r\n";
        return browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/resumes"))
                .header("X-CSRF-TOKEN", csrf(browser)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> uploadPart(HttpClient browser, String filename, byte[] bytes, String query) throws Exception {
        String boundary = "iras-matrix-" + UUID.randomUUID();
        var body = new java.io.ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.write(bytes);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/resumes" + (query == null ? "" : query)))
                .header("X-CSRF-TOKEN", csrf(browser)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "iras.test.ai", matches = "true")
    void realUploadExtractionEvidenceAndDeletionAreOwnerScoped() throws Exception {
        var owner = register();
        var other = register();
        var uploaded = upload(owner);
        assertThat(uploaded.statusCode()).isEqualTo(200);
        long id = json.readTree(uploaded.body()).path("data").get(0).path("resumecode").asLong();
        var detail = request(owner, "GET", "/IRAS/SingleResumePresent/?resumecode=" + id, null, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(json.readTree(detail.body()).path("data").path("技能专长").toString()).contains("Java");
        assertThat(request(owner, "GET", "/api/resumes/" + id + "/evidence", null, null).body()).contains("Spring Boot");
        assertThat(request(other, "GET", "/api/resumes/" + id + "/evidence", null, null).statusCode()).isEqualTo(404);
        assertThat(upload(owner).statusCode()).isEqualTo(409);
        long ownerId = json.readTree(request(owner, "GET", "/api/auth/me", null, null).body()).path("data").path("id").asLong();
        long otherId = json.readTree(request(other, "GET", "/api/auth/me", null, null).body()).path("data").path("id").asLong();
        assertThat(search.search(ownerId, "Java C++ [PostgreSQL]")).isNotEmpty().allSatisfy(row ->
            assertThat(((Number) row.get("resume_id")).longValue()).isEqualTo(id));
        assertThat(search.search(otherId, "Java")).isEmpty();
        assertThat(search.search(ownerId, "Java", new SearchService.Filters(80, null, null))).isEmpty();
        assertThat(search.search(ownerId, "Java", new SearchService.Filters(null, "nonexistent-degree", null))).isEmpty();
        assertThat(search.search(ownerId, "Java", new SearchService.Filters(null, null, java.util.List.of("nonexistent-skill")))).isEmpty();
        var grouped = search.search(ownerId, "Java", new SearchService.Filters(0, null, java.util.List.of("Java")));
        assertThat(grouped).hasSize(1);
        assertThat((java.util.List<?>) grouped.getFirst().get("evidence")).isNotEmpty();
        assertThatThrownBy(() -> search.search(ownerId, "Java", new SearchService.Filters(-1, null, null)))
            .isInstanceOf(ApiException.class);
        long evidenceId = database.queryForObject("SELECT min(id) FROM evidence WHERE resume_id=?", Long.class, id);
        var requirements = json.valueToTree(Map.of("mustHave", java.util.List.of("Java"), "preferred", java.util.List.of()));
        var guard = agent.new RunContext(ownerId, requirements);
        var foreignGuard = agent.new RunContext(otherId, requirements);
        assertThatThrownBy(() -> guard.save(new AgentRunner.Draft(java.util.List.of())))
            .isInstanceOf(ApiException.class).hasMessageContaining("Search must succeed");
        guard.search(new AgentRunner.SearchInput("Java", null));
        assertThatThrownBy(() -> guard.save(new AgentRunner.Draft(java.util.List.of())))
            .isInstanceOf(ApiException.class).hasMessageContaining("Retrieved resumes");
        var unknown = new AgentRunner.Draft(java.util.List.of(new AgentRunner.Candidate(id,
            java.util.List.of(new AgentRunner.Assessment("Java", "UNKNOWN", java.util.List.of())),
            java.util.List.of("Describe your Java project."))));
        assertThatThrownBy(() -> guard.save(unknown)).isInstanceOf(ApiException.class).hasMessageContaining("evidence read");
        assertThatThrownBy(() -> foreignGuard.read(new AgentRunner.EvidenceInput(java.util.List.of(evidenceId)))).isInstanceOf(ApiException.class);
        guard.read(new AgentRunner.EvidenceInput(java.util.List.of(evidenceId)));
        var fabricated = new AgentRunner.Draft(java.util.List.of(new AgentRunner.Candidate(id,
            java.util.List.of(new AgentRunner.Assessment("Java", "EVIDENCE", java.util.List.of(new AgentRunner.Citation(evidenceId, "Fabricated experience not in the document")))),
            java.util.List.of("Describe your Java project."))));
        assertThatThrownBy(() -> guard.save(fabricated)).isInstanceOf(ApiException.class).hasMessageContaining("source quote");
        long jobId = database.queryForObject("INSERT INTO jobs(owner_id,title,description,requirements,confirmed) VALUES (?, 'Synthetic role', 'Java PostgreSQL', CAST(? AS jsonb),true) RETURNING id", Long.class,
            ownerId, json.writeValueAsString(Map.of("mustHave", java.util.List.of("Java", "PostgreSQL"), "preferred", java.util.List.of(), "questions", java.util.List.of())));
        var started = request(owner, "POST", "/api/agent/tasks", Map.of("jobId", jobId,
            "request", "Find evidence for Java and PostgreSQL experience and prepare a draft with interview questions."), csrf(owner));
        assertThat(started.statusCode()).isEqualTo(202);
        String taskId = json.readTree(started.body()).path("data").path("id").asText();
        assertThat(request(other, "GET", "/api/agent/tasks/" + taskId, null, null).statusCode()).isEqualTo(404);
        assertThat(request(other, "POST", "/api/agent/tasks/" + taskId + "/cancel", Map.of(), csrf(other)).statusCode()).isEqualTo(404);
        var completed = owner.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/agent/tasks/" + taskId + "/events"))
            .timeout(java.time.Duration.ofSeconds(210)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(completed.statusCode()).isEqualTo(200);
        assertThat(completed.body()).contains("COMPLETED", "searchResumes", "readEvidence", "saveDraft");
        JsonNode task = json.readTree(request(owner, "GET", "/api/agent/tasks/" + taskId, null, null).body()).path("data");
        String reportId = task.path("reportId").asText();
        assertThat(request(other, "GET", "/api/reports/" + reportId, null, null).statusCode()).isEqualTo(404);
        assertThat(request(other, "POST", "/api/reports/" + reportId + "/approve", Map.of(), csrf(other)).statusCode()).isEqualTo(404);
        var approved = request(owner, "POST", "/api/reports/" + reportId + "/approve", Map.of(), csrf(owner));
        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(request(owner, "POST", "/api/reports/" + reportId + "/approve", Map.of(), csrf(owner)).body()).isEqualTo(approved.body());
        assertThat(request(other, "PUT", "/api/resumes/" + id + "/extraction", Map.of("技能专长", java.util.List.of("Java")), csrf(other)).statusCode()).isEqualTo(404);
        assertThat(request(owner, "PUT", "/api/resumes/" + id + "/extraction", Map.of("技能专长", java.util.List.of("Java")), csrf(owner)).statusCode()).isEqualTo(200);
        assertThat(database.queryForObject("SELECT version FROM resumes WHERE id=?", Integer.class, id)).isEqualTo(2);
        assertThat(database.queryForObject("SELECT count(*) FROM resume_versions WHERE resume_id=?", Integer.class, id)).isEqualTo(2);
        var corrected = search.search(ownerId, "Java", new SearchService.Filters(null, null, java.util.List.of("Java")));
        assertThat(corrected).hasSize(1);
        assertThat(corrected.getFirst().get("version")).isEqualTo(2);
        assertThat(corrected.toString()).contains("CORRECTION").doesNotContain("Spring Boot REST APIs");
        assertThatThrownBy(() -> agent.new RunContext(ownerId, requirements).read(new AgentRunner.EvidenceInput(java.util.List.of(evidenceId))))
            .isInstanceOf(ApiException.class).hasMessageContaining("not found");
        var statistics = request(owner, "GET", "/IRAS/ResumeStatistic/", null, null);
        assertThat(statistics.statusCode()).isEqualTo(200);
        assertThat(json.readTree(statistics.body()).path("data").path("degree_part").path("未知").asInt()).isEqualTo(1);
        assertThat(request(owner, "POST", "/api/reports/" + reportId + "/approve", Map.of(), csrf(owner)).statusCode()).isEqualTo(409);
        assertThat(request(other, "POST", "/IRAS/ResumeDelete/", Map.of("code_list", java.util.List.of(id)), csrf(other)).statusCode()).isEqualTo(404);
        assertThat(request(owner, "POST", "/IRAS/ResumeDelete/", Map.of("code_list", java.util.List.of(id)), csrf(owner)).statusCode()).isEqualTo(200);
        assertThat(database.queryForObject("SELECT count(*) FROM evidence WHERE resume_id=?", Integer.class, id)).isZero();
        assertThat(database.queryForObject("SELECT count(*) FROM resume_versions WHERE resume_id=?", Integer.class, id)).isZero();
        assertThat(request(owner, "GET", "/api/reports/" + reportId, null, null).statusCode()).isEqualTo(404);
    }

    @Test
        @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "iras.test.ai", matches = "true")
        void realJobExtractionConfirmationAndOwnership() throws Exception {
        var owner = register();
        var other = register();
        var created = request(owner, "POST", "/api/jobs", Map.of("title", "Java Engineer", "description",
            "Must have Java and PostgreSQL skills. Spring Boot is preferred. No experience threshold. Remote work."), csrf(owner));
        assertThat(created.statusCode()).isEqualTo(200);
        long id = json.readTree(created.body()).path("data").path("id").asLong();
        assertThat(request(other, "GET", "/api/jobs/" + id, null, null).statusCode()).isEqualTo(404);
        assertThat(request(owner, "GET", "/api/jobs", null, null).body()).contains("Java Engineer");
        var input = Map.of("title", "Java Engineer", "description", "Java and PostgreSQL",
            "requirements", Map.of("mustHave", java.util.List.of("Java", "PostgreSQL"), "preferred", java.util.List.of(), "questions", java.util.List.of("Experience threshold?")));
        assertThat(request(owner, "PUT", "/api/jobs/" + id, input, csrf(owner)).statusCode()).isEqualTo(200);
        assertThat(request(owner, "POST", "/api/jobs/" + id + "/confirm", Map.of(), csrf(owner)).statusCode()).isEqualTo(409);
        var resolved = Map.of("title", "Java Engineer", "description", "Java and PostgreSQL",
            "requirements", Map.of("mustHave", java.util.List.of("Java", "PostgreSQL"), "preferred", java.util.List.of(), "questions", java.util.List.of()));
        assertThat(request(other, "PUT", "/api/jobs/" + id, resolved, csrf(other)).statusCode()).isEqualTo(404);
        assertThat(request(owner, "PUT", "/api/jobs/" + id, resolved, csrf(owner)).statusCode()).isEqualTo(200);
        assertThat(request(owner, "POST", "/api/jobs/" + id + "/confirm", Map.of(), csrf(owner)).statusCode()).isEqualTo(200);
        assertThat(database.queryForObject("SELECT confirmed FROM jobs WHERE id=?", Boolean.class, id)).isTrue();
        }

        @Test
    void registrationLoginLogoutAndIsolationWorkOverRealHttpAndDatabase() throws Exception {
        var browser = client();
        var input = Map.of("user_account", "synthetic-" + UUID.randomUUID(), "password", "Synthetic-Password-2026");
        assertThat(request(browser, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
        assertThat(request(browser, "POST", "/IRAS/UserRegister/", input, null).statusCode()).isEqualTo(403);
        var registered = request(browser, "POST", "/IRAS/UserRegister/", input, csrf(browser));
        assertThat(registered.statusCode()).isEqualTo(200);
        JsonNode identity = json.readTree(registered.body());
        assertThat(identity.get("user_id").asLong()).isPositive();
        String hash = database.queryForObject("SELECT password_hash FROM accounts WHERE id=?", String.class,
                identity.get("user_id").asLong());
        assertThat(hash).startsWith("$2a$").isNotEqualTo(input.get("password"));
        assertThat(request(browser, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
        assertThat(request(client(), "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
        assertThat(request(browser, "POST", "/IRAS/UserRegister/", input, csrf(browser)).statusCode()).isEqualTo(409);
        assertThat(request(browser, "POST", "/api/auth/logout", Map.of(), csrf(browser)).statusCode()).isEqualTo(200);
        assertThat(request(browser, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
        assertThat(request(browser, "POST", "/IRAS/UserLogin/", Map.of("user_account", input.get("user_account"),
                "password", "incorrect"), csrf(browser)).statusCode()).isEqualTo(401);
        assertThat(request(browser, "POST", "/IRAS/UserLogin/", input, csrf(browser)).statusCode()).isEqualTo(200);
        assertThat(request(browser, "POST", "/IRAS/AdminLogin/", input, csrf(browser)).statusCode()).isEqualTo(401);
    }

    @Test
    void cancellationStopsBeforeCallingModelAndWaitingTaskCanBeCancelled() throws Exception {
        var user = register();
        long owner = json.readTree(request(user, "GET", "/api/auth/me", null, null).body()).path("data").path("id").asLong();
        long jobId = database.queryForObject("INSERT INTO jobs(owner_id,title,description,requirements,confirmed) VALUES (?,'Synthetic cancel role','Java','{\"mustHave\":[\"Java\"],\"preferred\":[],\"questions\":[]}',true) RETURNING id", Long.class, owner);
        UUID id = UUID.randomUUID();
        database.update("INSERT INTO agent_tasks(id,owner_id,job_id,job_version,request,state,clarification) VALUES (?,?,?,1,'Synthetic request','WAITING','Which project?')", id, owner, jobId);
        var cancelled = request(user, "POST", "/api/agent/tasks/" + id + "/cancel", Map.of(), csrf(user));
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(cancelled.body()).contains("CANCELLED");
        assertThat(request(user, "POST", "/api/agent/tasks/" + id + "/reply", Map.of("answer", "Java"), csrf(user)).statusCode()).isEqualTo(409);
        assertThatThrownBy(() -> agent.run(owner, json.valueToTree(Map.of("mustHave", java.util.List.of("Java"))), "Synthetic request",
                () -> { throw new ApiException(409, "Task cancelled"); }, event -> { throw new AssertionError("No model event expected"); }))
                .isInstanceOf(ApiException.class).hasMessage("Task cancelled");
    }

    @Test
    void jobDeletionCascadesAndInvalidParametersReturnClientErrors() throws Exception {
        var user = register();
        var other = register();
        long owner = json.readTree(request(user, "GET", "/api/auth/me", null, null).body()).path("data").path("id").asLong();
        long job = database.queryForObject("INSERT INTO jobs(owner_id,title,description,requirements,confirmed) VALUES (?,'Synthetic delete','Java','{\"mustHave\":[\"Java\"],\"preferred\":[],\"questions\":[]}',true) RETURNING id", Long.class, owner);
        UUID task = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        database.update("INSERT INTO agent_tasks(id,owner_id,job_id,job_version,request,state) VALUES (?,?,?,1,'Synthetic','COMPLETED')", task, owner, job);
        database.update("INSERT INTO reports(id,task_id,owner_id,job_id,job_version,body) VALUES (?,?,?,?,1,'{}')", report, task, owner, job);
        assertThat(request(other, "DELETE", "/api/jobs/" + job, null, csrf(other)).statusCode()).isEqualTo(404);
        assertThat(request(user, "GET", "/api/jobs/not-a-number", null, null).statusCode()).isEqualTo(400);
        assertThat(request(user, "GET", "/api/does-not-exist", null, null).statusCode()).isEqualTo(404);
        assertThat(request(user, "PATCH", "/api/jobs/" + job, Map.of(), csrf(user)).statusCode()).isEqualTo(405);
        assertThat(request(user, "GET", "/IRAS/SingleResumePresent/", null, null).statusCode()).isEqualTo(400);
        assertThat(request(user, "DELETE", "/api/jobs/" + job, null, csrf(user)).statusCode()).isEqualTo(200);
        assertThat(request(user, "GET", "/api/jobs/" + job, null, null).statusCode()).isEqualTo(404);
        assertThat(database.queryForObject("SELECT count(*) FROM agent_tasks WHERE id=?", Integer.class, task)).isZero();
        assertThat(database.queryForObject("SELECT count(*) FROM reports WHERE id=?", Integer.class, report)).isZero();
    }

    @Test
    void uploadValidationRejectsInvalidInputsWithoutModel() throws Exception {
        var browser = register();
        String noPartBoundary = "iras-matrix-empty";
        var noPart = browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/resumes"))
                .header("X-CSRF-TOKEN", csrf(browser)).header("Content-Type", "multipart/form-data; boundary=" + noPartBoundary)
                .POST(HttpRequest.BodyPublishers.ofString("--" + noPartBoundary + "--\r\n")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(noPart.statusCode()).isEqualTo(400);
        String boundary = "iras-matrix-batch";
        StringBuilder batch = new StringBuilder();
        for (int index = 0; index < 6; index++) {
            batch.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"batch-")
                    .append(index).append(".txt\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nSynthetic ").append(index).append("\r\n");
        }
        batch.append("--").append(boundary).append("--\r\n");
        var six = browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/resumes"))
                .header("X-CSRF-TOKEN", csrf(browser)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(batch.toString())).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(six.statusCode()).isEqualTo(400);
        assertThat(six.body()).contains("Choose 1 to 5 files");
        var image = uploadPart(browser, "synthetic.png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}, null);
        assertThat(image.statusCode()).isEqualTo(400);
        assertThat(image.body()).contains("Only TXT, PDF and DOCX are supported");
        var empty = uploadPart(browser, "empty.txt", new byte[0], null);
        assertThat(empty.statusCode()).isEqualTo(400);
        assertThat(empty.body()).contains("Invalid file name or size");
        var oversized = uploadPart(browser, "long.txt", "a".repeat(50001).getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        assertThat(oversized.statusCode()).isEqualTo(400);
        assertThat(oversized.body()).contains("Text exceeds 50000 characters");
        var foreignOwner = uploadPart(browser, "owner.txt", "Synthetic owner check".getBytes(java.nio.charset.StandardCharsets.UTF_8), "?user_id=999999");
        assertThat(foreignOwner.statusCode()).isEqualTo(403);
        assertThat(uploadPart(client(), "anonymous.txt", "Synthetic anonymous check".getBytes(java.nio.charset.StandardCharsets.UTF_8), null).statusCode()).isEqualTo(401);
        assertThat(json.readTree(image.body()).path("msg").asText()).isEqualTo("error");
    }

    @Test
    void failedFileCleanupIsPersistedAndRetried() throws Exception {
        java.nio.file.Files.createDirectories(STORAGE.resolve("synthetic-cleanup"));
        java.nio.file.Files.writeString(STORAGE.resolve("synthetic-cleanup/child.txt"), "synthetic");
        cleanupFiles.enqueue("synthetic-cleanup");
        assertThat(cleanupFiles.remove("synthetic-cleanup")).isFalse();
        assertThat(database.queryForObject("SELECT attempts FROM file_cleanup WHERE file_path='synthetic-cleanup'", Integer.class)).isPositive();
        java.nio.file.Files.delete(STORAGE.resolve("synthetic-cleanup/child.txt"));
        cleanupFiles.retry();
        assertThat(java.nio.file.Files.exists(STORAGE.resolve("synthetic-cleanup"))).isFalse();
        assertThat(database.queryForObject("SELECT count(*) FROM file_cleanup WHERE file_path='synthetic-cleanup'", Integer.class)).isZero();
    }

    @Test
    void exhaustedRecoveryNotifiesSseAndDoesNotRetry() throws Exception {
        var user = register();
        long owner = json.readTree(request(user, "GET", "/api/auth/me", null, null).body()).path("data").path("id").asLong();
        long job = database.queryForObject("INSERT INTO jobs(owner_id,title,description,requirements,confirmed) VALUES (?,'Synthetic exhausted','Java','{}',true) RETURNING id", Long.class, owner);
        UUID task = UUID.randomUUID();
        database.update("INSERT INTO agent_tasks(id,owner_id,job_id,job_version,request,state,attempts,lease_until) VALUES (?,?,?,1,'Synthetic','RUNNING',2,now()+interval '2 seconds')", task, owner, job);
        var response = user.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/agent/tasks/" + task + "/events"))
                .timeout(java.time.Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("RUNNING", "FAILED", "Recovery attempts exhausted");
        assertThat(database.queryForObject("SELECT attempts FROM agent_tasks WHERE id=?", Integer.class, task)).isEqualTo(2);
        assertThat(database.queryForObject("SELECT count(*) FROM reports WHERE task_id=?", Integer.class, task)).isZero();
    }

        @Test
        @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "iras.test.ai", matches = "true")
        void expiredLeaseRecoversAndRealClarificationResumes() throws Exception {
        var user = register();
        long owner = json.readTree(request(user, "GET", "/api/auth/me", null, null).body()).path("data").path("id").asLong();
        long job = database.queryForObject("INSERT INTO jobs(owner_id,title,description,requirements,confirmed) VALUES (?,'Synthetic recovery','Java','{\"mustHave\":[\"Java\"],\"preferred\":[],\"questions\":[]}',true) RETURNING id", Long.class, owner);
        UUID recovered = UUID.randomUUID();
        database.update("INSERT INTO agent_tasks(id,owner_id,job_id,job_version,request,state,attempts,lease_until) VALUES (?,?,?,1,'Search my resumes for Java. If none exist save an empty draft.','RUNNING',1,now()-interval '1 second')", recovered, owner, job);
        String recoveryEvents = user.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/agent/tasks/" + recovered + "/events"))
            .timeout(java.time.Duration.ofSeconds(210)).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
        assertThat(recoveryEvents).contains("COMPLETED");
        assertThat(database.queryForObject("SELECT attempts FROM agent_tasks WHERE id=?", Integer.class, recovered)).isEqualTo(2);
        assertThat(database.queryForObject("SELECT count(*) FROM reports WHERE task_id=?", Integer.class, recovered)).isEqualTo(1);

        var started = request(user, "POST", "/api/agent/tasks", Map.of("jobId", job,
            "request", "Before searching I need to clarify which Java project to discuss. Call askClarification now to ask me which project. Once I reply, search my resumes and save a draft; do not ask again."), csrf(user));
        assertThat(started.statusCode()).isEqualTo(202);
        String task = json.readTree(started.body()).path("data").path("id").asText();
        String questionEvents = user.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/agent/tasks/" + task + "/events"))
            .timeout(java.time.Duration.ofSeconds(210)).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
        assertThat(questionEvents).contains("WAITING", "askClarification");
        assertThat(request(user, "POST", "/api/agent/tasks/" + task + "/reply", Map.of("answer", "The Java REST API project. Proceed with the confirmed Java requirement. I have no resumes yet; save an empty draft if search finds none."), csrf(user)).statusCode()).isEqualTo(200);
        String replyEvents = user.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/agent/tasks/" + task + "/events"))
            .timeout(java.time.Duration.ofSeconds(210)).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
        assertThat(replyEvents).contains("COMPLETED", "searchResumes", "saveDraft");
        assertThat(database.queryForObject("SELECT request FROM agent_tasks WHERE id=?", String.class, UUID.fromString(task)))
            .contains("Clarification:", "User reply:", "Java REST API project");
        }

    @Test
    void administratorManagementKeepsSessionAndInvalidatesDeletedUser() throws Exception {
        var user = register();
        var identity = json.readTree(request(user, "GET", "/api/auth/me", null, null).body()).path("data");
        var administrator = register();
        var adminIdentity = json.readTree(request(administrator, "GET", "/api/auth/me", null, null).body()).path("data");
        database.update("UPDATE accounts SET role='ADMIN' WHERE id=?", adminIdentity.path("id").asLong());
        assertThat(request(administrator, "POST", "/IRAS/AdminLogin/", Map.of("account", adminIdentity.path("account").asText(),
                "password", "Synthetic-Password-2026"), csrf(administrator)).statusCode()).isEqualTo(200);
        assertThat(request(user, "GET", "/IRAS/FindAllUsers/", null, null).statusCode()).isEqualTo(403);
        var users = request(administrator, "GET", "/IRAS/FindAllUsers/", null, null);
        assertThat(users.statusCode()).isEqualTo(200);
        assertThat(users.body()).doesNotContain("password");
        assertThat(request(administrator, "POST", "/api/admin/users", Map.of("user_account", "synthetic-" + UUID.randomUUID(), "password", "Synthetic-Password-2026"), csrf(administrator)).statusCode()).isEqualTo(200);
        assertThat(request(administrator, "GET", "/api/auth/me", null, null).body()).contains("ADMIN");
        assertThat(request(administrator, "POST", "/IRAS/UserDelete/", Map.of("user_id", identity.path("id").asLong()), csrf(administrator)).statusCode()).isEqualTo(200);
        assertThat(request(user, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
    }
}