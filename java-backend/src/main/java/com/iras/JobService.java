package com.iras;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

@Service
public class JobService {
    private final JdbcTemplate database;
    private final TransactionTemplate transactions;
    private final AiService ai;
    private final ObjectMapper json;

    public JobService(JdbcTemplate database, TransactionTemplate transactions, AiService ai, ObjectMapper json) {
        this.database = database;
        this.transactions = transactions;
        this.ai = ai;
        this.json = json;
    }

    public Map<String, Object> create(long owner, String title, String description) {
        validateText(title, description);
        var requirements = ai.structured("Analyze this job description. Return arrays of strings: mustHave, preferred, questions. "
                + "Only job-related skills and experience. Exclude age, gender, birthplace, political affiliation and other protected attributes. "
                + "Ask questions for ambiguous requirements; never silently invent or relax requirements.", description);
        validateRequirements(requirements);
        Long id = database.queryForObject("INSERT INTO jobs(owner_id,title,description,requirements) VALUES (?,?,?,CAST(? AS jsonb)) RETURNING id",
                Long.class, owner, title.strip(), description.strip(), requirements.toString());
        return get(id, owner);
    }

    public Map<String, Object> get(long id, long owner) {
        var rows = database.queryForList("SELECT id,title,description,requirements::text,confirmed,version FROM jobs WHERE id=? AND owner_id=?", id, owner);
        if (rows.isEmpty()) throw new ApiException(404, "Job not found");
        var row = rows.getFirst();
        try { row.put("requirements", json.readTree((String) row.get("requirements"))); }
        catch (Exception error) { throw new ApiException(500, "Invalid stored job"); }
        return row;
    }

    public List<Map<String, Object>> list(long owner) {
        return database.queryForList("SELECT id,title,confirmed,version,created_at FROM jobs WHERE owner_id=? ORDER BY id DESC", owner);
    }

    public Map<String, Object> update(long id, long owner, String title, String description, JsonNode requirements) {
        validateText(title, description);
        validateRequirements(requirements);
        return transactions.execute(status -> {
            if (database.update("UPDATE jobs SET title=?,description=?,requirements=CAST(? AS jsonb),confirmed=false,version=version+1 WHERE id=? AND owner_id=?",
                    title.strip(), description.strip(), requirements.toString(), id, owner) != 1) throw new ApiException(404, "Job not found");
            database.update("UPDATE reports SET status='STALE' WHERE job_id=? AND owner_id=?", id, owner);
            database.update("UPDATE agent_tasks SET state='CANCELLED',updated_at=now() WHERE job_id=? AND owner_id=? AND state IN ('QUEUED','RUNNING','WAITING')", id, owner);
            return get(id, owner);
        });
    }

    public Map<String, Object> confirm(long id, long owner) {
        return transactions.execute(status -> {
            database.queryForList("SELECT id FROM jobs WHERE id=? AND owner_id=? FOR UPDATE", id, owner);
            var job = get(id, owner);
            var requirements = (JsonNode) job.get("requirements");
            if (!requirements.path("questions").isEmpty()) throw new ApiException(409, "Resolve clarification questions before confirmation");
            if (requirements.path("mustHave").isEmpty()) throw new ApiException(400, "At least one job-related requirement is required");
            database.update("UPDATE jobs SET confirmed=true WHERE id=? AND owner_id=?", id, owner);
            return get(id, owner);
        });
    }

    public void delete(long id, long owner) {
        transactions.executeWithoutResult(status -> {
            if (database.queryForList("SELECT id FROM jobs WHERE id=? AND owner_id=? FOR UPDATE", id, owner).isEmpty())
                throw new ApiException(404, "Job not found");
            database.update("DELETE FROM jobs WHERE id=? AND owner_id=?", id, owner);
        });
    }

    private void validateText(String title, String description) {
        if (title == null || title.isBlank() || title.length() > 160 || description == null || description.isBlank()
                || description.length() > 10000) throw new ApiException(400, "Invalid job title or description");
    }

    void validateRequirements(JsonNode value) {
        if (value == null || !value.isObject()) throw new ApiException(400, "Invalid requirements");
        for (String key : List.of("mustHave", "preferred", "questions")) {
            if (!value.path(key).isArray() || value.path(key).size() > 20) throw new ApiException(400, "Requirements must contain bounded string arrays");
            for (JsonNode item : value.path(key)) {
                if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 500) throw new ApiException(400, "Invalid requirement item");
                if (!key.equals("questions") && java.util.regex.Pattern.compile(
                        "年龄|性别|籍贯|政治面貌|婚育|民族|\\b(age|gender|race|ethnicity|birthplace|religion)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(item.asText()).find()) throw new ApiException(400, "Use job-related skills and experience only");
            }
        }
    }
}