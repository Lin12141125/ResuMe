package com.iras;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Service
public class AgentTaskService {
    public record Changed(UUID id) {}
    private final JdbcTemplate database;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final JobService jobs;
    private final AgentRunner runner;
    private final ApplicationEventPublisher events;

    public AgentTaskService(JdbcTemplate database, TransactionTemplate transactions, ObjectMapper json,
                            JobService jobs, AgentRunner runner, ApplicationEventPublisher events) {
        this.database = database;
        this.transactions = transactions;
        this.json = json;
        this.jobs = jobs;
        this.runner = runner;
        this.events = events;
    }

    public Map<String, Object> create(long owner, long jobId, String request) {
        validateRequest(request);
        return transactions.execute(status -> {
            database.queryForList("SELECT id FROM accounts WHERE id=? FOR UPDATE", owner);
            database.queryForList("SELECT id FROM jobs WHERE id=? AND owner_id=? FOR UPDATE", jobId, owner);
            var job = jobs.get(jobId, owner);
            if (!Boolean.TRUE.equals(job.get("confirmed"))) throw new ApiException(409, "Confirm job requirements first");
            if (database.queryForObject("SELECT count(*) FROM agent_tasks WHERE owner_id=? AND state IN ('QUEUED','RUNNING','WAITING')", Integer.class, owner) >= 3)
                throw new ApiException(429, "Too many active tasks");
            UUID id = UUID.randomUUID();
            database.update("INSERT INTO agent_tasks(id,owner_id,job_id,job_version,request,state) VALUES (?,?,?,?,?,'QUEUED')",
                    id, owner, jobId, job.get("version"), request.strip());
            return get(id, owner);
        });
    }

    public Map<String, Object> get(UUID id, long owner) {
        var rows = database.queryForList("SELECT id,job_id,job_version,state,clarification,trace::text,error,attempts,created_at,updated_at FROM agent_tasks WHERE id=? AND owner_id=?", id, owner);
        if (rows.isEmpty()) throw new ApiException(404, "Task not found");
        var row = rows.getFirst();
        row.put("trace", parse((String) row.get("trace")));
        var reports = database.queryForList("SELECT id FROM reports WHERE task_id=? AND owner_id=?", id, owner);
        row.put("reportId", reports.isEmpty() ? null : reports.getFirst().get("id"));
        return row;
    }

    public Map<String, Object> reply(UUID id, long owner, String answer) {
        validateRequest(answer);
        transactions.executeWithoutResult(status -> {
            var task = get(id, owner);
            database.queryForList("SELECT id FROM jobs WHERE id=? AND owner_id=? FOR UPDATE", task.get("job_id"), owner);
            var job = jobs.get(((Number) task.get("job_id")).longValue(), owner);
            if (!job.get("version").equals(task.get("job_version")) || !Boolean.TRUE.equals(job.get("confirmed")))
                throw new ApiException(409, "Job has changed");
            int updated = database.update("UPDATE agent_tasks SET request=request || E'\\nClarification: ' || clarification || E'\\nUser reply: ' || ?,state='QUEUED',clarification=NULL,attempts=0,updated_at=now() "
                    + "WHERE id=? AND owner_id=? AND state='WAITING' AND length(request)+length(clarification)+length(?)<16000", answer, id, owner, answer);
            if (updated != 1) throw new ApiException(409, "Task is not waiting or conversation is too long");
        });
        events.publishEvent(new Changed(id));
        return get(id, owner);
    }

    public Map<String, Object> cancel(UUID id, long owner) {
        get(id, owner);
        database.update("UPDATE agent_tasks SET state='CANCELLED',lease_until=NULL,updated_at=now() WHERE id=? AND owner_id=? AND state IN ('QUEUED','RUNNING','WAITING')", id, owner);
        events.publishEvent(new Changed(id));
        return get(id, owner);
    }

    @Scheduled(fixedDelayString = "${iras.agent.poll-ms:1000}")
    public void processNext() {
        var exhausted = transactions.execute(status -> database.queryForList(
                "UPDATE agent_tasks SET state='FAILED',error='Recovery attempts exhausted',lease_until=NULL,updated_at=now() "
                        + "WHERE state='RUNNING' AND lease_until<now() AND attempts>=2 RETURNING id", UUID.class));
        exhausted.forEach(id -> events.publishEvent(new Changed(id)));
        Map<String, Object> task = transactions.execute(status -> {
            var rows = database.queryForList("SELECT * FROM agent_tasks WHERE state='QUEUED' OR (state='RUNNING' AND lease_until<now() AND attempts<2) ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1");
            if (rows.isEmpty()) return null;
            var row = rows.getFirst();
            database.update("UPDATE agent_tasks SET state='RUNNING',attempts=attempts+1,lease_until=now()+interval '360 seconds',updated_at=now() WHERE id=?", row.get("id"));
            row.put("attempts", ((Number) row.get("attempts")).intValue() + 1);
            return row;
        });
        if (task == null) return;
        UUID id = (UUID) task.get("id");
        long owner = ((Number) task.get("owner_id")).longValue();
        int attempt = ((Number) task.get("attempts")).intValue();
        try {
            events.publishEvent(new Changed(id));
            var job = jobs.get(((Number) task.get("job_id")).longValue(), owner);
            if (!job.get("version").equals(task.get("job_version")) || !Boolean.TRUE.equals(job.get("confirmed")))
                throw new ApiException(409, "Job changed before execution");
            var outcome = runner.run(owner, (JsonNode) job.get("requirements"), (String) task.get("request"),
                    () -> ensureActive(id, attempt), event -> progress(id, attempt, event));
            finish(task, outcome);
        } catch (Exception error) {
            String message = error instanceof ApiException ? error.getMessage() : "Agent execution failed";
            database.update("UPDATE agent_tasks SET state='FAILED',error=?,lease_until=NULL,updated_at=now() WHERE id=? AND state='RUNNING' AND attempts=?", message, id, attempt);
        } finally {
            events.publishEvent(new Changed(id));
        }
    }

    private void ensureActive(UUID id, int attempt) {
        if (database.queryForObject("SELECT count(*) FROM agent_tasks WHERE id=? AND state='RUNNING' AND attempts=? AND lease_until>now()", Integer.class, id, attempt) != 1)
            throw new ApiException(409, "Task cancelled or lease expired");
    }

    private void progress(UUID id, int attempt, Map<String, Object> event) {
        var record = new LinkedHashMap<>(event);
        record.put("attempt", attempt);
        database.update("UPDATE agent_tasks SET trace=trace || CAST(? AS jsonb),updated_at=now() WHERE id=? AND state='RUNNING' AND attempts=?",
                json.valueToTree(List.of(record)).toString(), id, attempt);
        events.publishEvent(new Changed(id));
    }

    private void finish(Map<String, Object> task, AgentRunner.Outcome outcome) {
        transactions.executeWithoutResult(status -> {
            UUID id = (UUID) task.get("id");
            long owner = ((Number) task.get("owner_id")).longValue();
            database.queryForList("SELECT id FROM jobs WHERE id=? AND owner_id=? FOR UPDATE", task.get("job_id"), owner);
            var job = jobs.get(((Number) task.get("job_id")).longValue(), owner);
            if (!job.get("version").equals(task.get("job_version")) || !Boolean.TRUE.equals(job.get("confirmed"))) throw new ApiException(409, "Job changed during execution");
            for (var entry : new TreeMap<>(outcome.versions()).entrySet()) {
                var rows = database.queryForList("SELECT version FROM resumes WHERE id=? AND owner_id=? FOR UPDATE", entry.getKey(), owner);
                if (rows.isEmpty() || !rows.getFirst().get("version").equals(entry.getValue())) throw new ApiException(409, "Resume changed during execution");
            }
            database.queryForList("SELECT id FROM agent_tasks WHERE id=? FOR UPDATE", id);
            ensureActive(id, ((Number) task.get("attempts")).intValue());
            if (outcome.clarification() != null) {
                database.update("UPDATE agent_tasks SET state='WAITING',clarification=?,lease_until=NULL,updated_at=now() WHERE id=?", outcome.clarification(), id);
                return;
            }
            UUID reportId = UUID.randomUUID();
            database.update("INSERT INTO reports(id,task_id,owner_id,job_id,job_version,body) VALUES (?,?,?,?,?,CAST(? AS jsonb))", reportId,
                    id, owner, task.get("job_id"), task.get("job_version"), json.valueToTree(outcome.draft()).toString());
            outcome.versions().forEach((resume, version) -> database.update("INSERT INTO report_resumes(report_id,resume_id,resume_version) VALUES (?,?,?)", reportId, resume, version));
            database.update("UPDATE agent_tasks SET state='COMPLETED',lease_until=NULL,updated_at=now() WHERE id=?", id);
        });
    }

    public List<Map<String, Object>> reports(long owner) {
        return database.queryForList("SELECT id,task_id,job_id,status,created_at FROM reports WHERE owner_id=? ORDER BY created_at DESC", owner);
    }

    public Map<String, Object> report(UUID id, long owner) {
        var rows = database.queryForList("SELECT id,task_id,job_id,job_version,body::text,status,approved_at,created_at FROM reports WHERE id=? AND owner_id=?", id, owner);
        if (rows.isEmpty()) throw new ApiException(404, "Report not found");
        var row = rows.getFirst();
        row.put("body", parse((String) row.get("body")));
        row.put("humanReviewRequired", true);
        return row;
    }

    public Map<String, Object> approve(UUID id, long owner) {
        var initial = report(id, owner);
        boolean accepted = Boolean.TRUE.equals(transactions.execute(status -> {
            database.queryForList("SELECT id FROM jobs WHERE id=? AND owner_id=? FOR UPDATE", initial.get("job_id"), owner);
            var job = jobs.get(((Number) initial.get("job_id")).longValue(), owner);
            var sources = database.queryForList("SELECT r.id,r.version,rr.resume_version FROM report_resumes rr JOIN resumes r ON r.id=rr.resume_id WHERE rr.report_id=? ORDER BY r.id FOR UPDATE OF r", id);
            database.queryForList("SELECT id FROM reports WHERE id=? AND owner_id=? FOR UPDATE", id, owner);
            var current = report(id, owner);
            boolean valid = !current.get("status").equals("STALE") && Boolean.TRUE.equals(job.get("confirmed"))
                    && job.get("version").equals(current.get("job_version")) && sources.stream().allMatch(row -> row.get("version").equals(row.get("resume_version")));
            if (!valid) {
                database.update("UPDATE reports SET status='STALE' WHERE id=? AND owner_id=?", id, owner);
                return false;
            }
            database.update("UPDATE reports SET status='APPROVED',approved_at=now() WHERE id=? AND owner_id=? AND status='DRAFT'", id, owner);
            return true;
        }));
        if (!accepted) throw new ApiException(409, "Report is stale; create a new task");
        return report(id, owner);
    }

    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (Exception error) { throw new ApiException(500, "Invalid stored task data"); }
    }

    private void validateRequest(String request) {
        if (request == null || request.isBlank() || request.length() > 4000) throw new ApiException(400, "Invalid task request");
    }
}