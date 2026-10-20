package com.iras;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeService {
    private final JdbcTemplate database;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final AiService ai;
    private final DocumentParser documents;
    private final Path storage;
    private final FileCleanup cleanup;

    public ResumeService(JdbcTemplate database, TransactionTemplate transactions, ObjectMapper json,
                         AiService ai, DocumentParser documents, @Value("${iras.storage}") String storage, FileCleanup cleanup) {
        this.database = database;
        this.transactions = transactions;
        this.json = json;
        this.ai = ai;
        this.documents = documents;
        this.storage = Path.of(storage).toAbsolutePath().normalize();
        this.cleanup = cleanup;
    }

    record Prepared(String name, byte[] bytes, String hash, List<DocumentParser.Fragment> fragments,
                    JsonNode extraction, List<float[]> vectors) {}

    public List<Map<String, Object>> upload(AccountPrincipal owner, List<MultipartFile> uploads) {
        if (!owner.role().equals("USER")) throw new ApiException(403, "Recruiter account required");
        if (uploads == null || uploads.isEmpty() || uploads.size() > 5) throw new ApiException(400, "Choose 1 to 5 files");
        var prepared = new ArrayList<Prepared>();
        var hashes = new HashSet<String>();
        try {
            for (var file : uploads) {
                String name = file.getOriginalFilename();
                if (name == null || name.isBlank() || name.length() > 60 || file.isEmpty() || file.getSize() > 10 * 1024 * 1024
                        || name.contains("/") || name.contains("\\")) throw new ApiException(400, "Invalid file name or size");
                byte[] bytes = file.getBytes();
                var fragments = documents.parse(name, bytes);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if (!hashes.add(hash) || database.queryForObject("SELECT count(*) FROM resumes WHERE owner_id=? AND sha256=?",
                        Integer.class, owner.id(), hash) > 0) throw new ApiException(409, "Duplicate document");
                prepared.add(new Prepared(name, bytes, hash, fragments, null, null));
            }
            var enriched = new ArrayList<Prepared>();
            for (var item : prepared) {
                String text = String.join("\n", item.fragments().stream().map(DocumentParser.Fragment::content).toList());
                var extraction = normalize(ai.structured(
                        "Extract a resume using these exact Chinese JSON keys. Strings: 姓名,出生日期,电话,邮箱,籍贯,政治面貌,最高学历,学校. "
                                + "年龄: integer (unknown 0). 技能专长,荣誉奖项: string arrays. 教育背景: objects with 时间,就读学校,就读专业. "
                                + "工作经历: objects with 时间,公司,职位,工作内容. Unknown strings: 无; unknown arrays: [].", text));
                var vectors = item.fragments().stream().map(fragment -> ai.embed(fragment.content())).toList();
                enriched.add(new Prepared(item.name(), item.bytes(), item.hash(), item.fragments(), extraction, vectors));
            }
            var created = new ArrayList<Path>();
            try {
                Files.createDirectories(storage);
                return transactions.execute(status -> {
                    if (database.queryForList("SELECT id FROM accounts WHERE id=? AND role='USER' FOR UPDATE", owner.id()).isEmpty())
                        throw new ApiException(401, "Account no longer exists");
                    var result = new ArrayList<Map<String, Object>>();
                    for (var item : enriched) {
                        String filename = UUID.randomUUID() + item.name().substring(item.name().lastIndexOf('.'));
                        Path path = storage.resolve(filename);
                        created.add(path);
                        try { Files.write(path, item.bytes()); }
                        catch (Exception error) { throw new ApiException(500, "File storage failed"); }
                        Long id = database.queryForObject("INSERT INTO resumes(owner_id,name,sha256,file_path,extraction,work_years) "
                                        + "VALUES (?,?,?,?,CAST(? AS jsonb),?) RETURNING id", Long.class,
                                owner.id(), item.name(), item.hash(), filename, item.extraction().toString(), workYears(item.extraction()));
                        database.update("INSERT INTO resume_versions(resume_id,version,extraction) VALUES (?,1,CAST(? AS jsonb))", id, item.extraction().toString());
                        for (int index = 0; index < item.fragments().size(); index++) {
                            var fragment = item.fragments().get(index);
                            database.update("INSERT INTO evidence(resume_id,page,paragraph,content,embedding) VALUES (?,?,?,?,CAST(? AS public.vector))",
                                    id, fragment.page(), fragment.paragraph(), fragment.content(), ai.vectorLiteral(item.vectors().get(index)));
                        }
                        result.add(Map.of("resumecode", id, "resumename", item.name()));
                    }
                    return result;
                });
            } catch (Exception error) {
                for (Path path : created) {
                    cleanup.enqueue(path.getFileName().toString());
                    cleanup.remove(path.getFileName().toString());
                }
                if (error instanceof ApiException apiError) throw apiError;
                if (error instanceof org.springframework.dao.DuplicateKeyException) throw new ApiException(409, "Duplicate document");
                throw new ApiException(500, "Resume persistence failed");
            }
        } catch (ApiException error) { throw error; }
        catch (Exception error) { throw new ApiException(400, "Invalid upload"); }
    }

    public Map<String, Object> find(long id, AccountPrincipal user) {
        var rows = database.queryForList("SELECT id,owner_id,name,version,extraction::text,file_path,work_years FROM resumes WHERE id=? AND (owner_id=? OR ?='ADMIN')",
                id, user.id(), user.role());
        if (rows.isEmpty()) throw new ApiException(404, "Resume not found");
        return rows.getFirst();
    }

    public JsonNode detail(long id, AccountPrincipal user) {
        return decode((String) find(id, user).get("extraction"));
    }

    JsonNode decode(String text) {
        try { return json.readTree(text); }
        catch (Exception error) { throw new ApiException(500, "Stored JSON is invalid"); }
    }

    public List<Map<String, Object>> list(AccountPrincipal user, Long requestedOwner) {
        long owner = requestedOwner == null ? user.id() : requestedOwner;
        if (owner != user.id() && !user.role().equals("ADMIN")) throw new ApiException(403, "Forbidden owner");
        return database.queryForList("SELECT id AS resumecode,name AS resumename,'已解析' AS state,version,work_years FROM resumes WHERE owner_id=? ORDER BY id DESC", owner);
    }

    public List<Map<String, Object>> evidence(long id, AccountPrincipal user) {
        find(id, user);
        return database.queryForList("SELECT id,resume_id,page,paragraph,content,version,source FROM evidence WHERE resume_id=? ORDER BY version,page,paragraph", id);
    }

    public boolean delete(List<Long> ids, AccountPrincipal user) {
        if (ids == null || ids.isEmpty() || ids.size() > 100) throw new ApiException(400, "Invalid resume IDs");
        var rows = ids.stream().distinct().map(id -> find(id, user)).toList();
        transactions.executeWithoutResult(status -> {
            for (var row : rows) {
                database.queryForList("SELECT id FROM resumes WHERE id=? FOR UPDATE", row.get("id"));
                cleanup.enqueue((String) row.get("file_path"));
                database.update("DELETE FROM agent_tasks WHERE id IN (SELECT p.task_id FROM reports p JOIN report_resumes rr ON rr.report_id=p.id WHERE rr.resume_id=?)", row.get("id"));
                database.update("UPDATE agent_tasks SET state='CANCELLED',updated_at=now() WHERE owner_id=? AND state IN ('QUEUED','RUNNING','WAITING')", row.get("owner_id"));
                database.update("DELETE FROM resumes WHERE id=?", row.get("id"));
            }
        });
        boolean pending = false;
        for (var row : rows) if (!cleanup.remove((String) row.get("file_path"))) pending = true;
        return pending;
    }

    public boolean deleteUser(long owner) {
        var paths = transactions.execute(status -> {
            if (database.queryForList("SELECT id FROM accounts WHERE id=? AND role='USER' FOR UPDATE", owner).isEmpty()) throw new ApiException(404, "User not found");
            database.queryForList("SELECT id FROM jobs WHERE owner_id=? ORDER BY id FOR UPDATE", owner);
            var files = database.queryForList("SELECT file_path FROM resumes WHERE owner_id=? ORDER BY id FOR UPDATE", String.class, owner);
            files.forEach(cleanup::enqueue);
            database.update("DELETE FROM accounts WHERE id=? AND role='USER'", owner);
            return files;
        });
        boolean pending = false;
        for (String path : paths) if (!cleanup.remove(path)) pending = true;
        return pending;
    }

    public ObjectNode normalize(JsonNode input) {
        if (input == null || !input.isObject()) throw new ApiException(400, "Extraction must be an object");
        var result = json.createObjectNode();
        for (String key : List.of("姓名", "出生日期", "电话", "邮箱", "籍贯", "政治面貌", "最高学历", "学校")) {
            String value = input.path(key).asText("无").strip();
            result.put(key, value.isBlank() ? "无" : value);
        }
        int age = input.path("年龄").asInt(0);
        result.put("年龄", age >= 0 && age <= 120 ? age : 0);
        for (String key : List.of("技能专长", "荣誉奖项", "教育背景", "工作经历")) {
            var array = result.putArray(key);
            if (input.path(key).isArray()) for (var element : input.path(key)) {
                if ((key.equals("教育背景") || key.equals("工作经历")) ? element.isObject() : element.isTextual()) array.add(element);
            }
        }
        return result;
    }

    public JsonNode correct(long id, JsonNode input, AccountPrincipal user) {
        var result = normalize(input);
        int previousVersion = ((Number) find(id, user).get("version")).intValue();
        var professional = json.createObjectNode();
        for (String key : List.of("最高学历", "学校", "技能专长", "荣誉奖项", "教育背景", "工作经历"))
            professional.set(key, result.get(key));
        byte[] text = professional.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var fragments = documents.parse("correction.txt", text);
        var vectors = fragments.stream().map(fragment -> ai.embed(fragment.content())).toList();
        return transactions.execute(status -> {
            var locked = database.queryForList("SELECT version FROM resumes WHERE id=? AND (owner_id=? OR ?='ADMIN') FOR UPDATE", id, user.id(), user.role());
            if (locked.isEmpty()) throw new ApiException(404, "Resume not found");
            if (((Number) locked.getFirst().get("version")).intValue() != previousVersion)
                throw new ApiException(409, "Resume changed while preparing correction; reload and retry");
            database.update("UPDATE resumes SET extraction=CAST(? AS jsonb),version=version+1,work_years=? WHERE id=?", result.toString(), workYears(result), id);
            database.update("INSERT INTO resume_versions(resume_id,version,extraction) SELECT id,version,extraction FROM resumes WHERE id=?", id);
            for (int index = 0; index < fragments.size(); index++) {
                var fragment = fragments.get(index);
                database.update("INSERT INTO evidence(resume_id,page,paragraph,content,embedding,version,source) VALUES (?,0,?,?,CAST(? AS public.vector),?,'CORRECTION')",
                        id, fragment.paragraph(), fragment.content(), ai.vectorLiteral(vectors.get(index)), previousVersion + 1);
            }
            database.update("UPDATE reports SET status='STALE' WHERE id IN (SELECT report_id FROM report_resumes WHERE resume_id=?)", id);
            database.update("UPDATE agent_tasks SET state='CANCELLED',updated_at=now() WHERE owner_id=(SELECT owner_id FROM resumes WHERE id=?) AND state IN ('QUEUED','RUNNING','WAITING')", id);
            return result;
        });
    }

    public static int workYears(JsonNode extraction) {
        var pattern = Pattern.compile("((?:19|20)\\d{2})(?:[./年-](\\d{1,2})(?!\\d))?");
        var intervals = new ArrayList<int[]>();
        YearMonth today = YearMonth.now();
        int current = today.getYear() * 12 + today.getMonthValue() - 1;
        for (var experience : extraction.path("工作经历")) {
            String period = experience.path("时间").asText();
            var matcher = pattern.matcher(period);
            var dates = new ArrayList<Integer>();
            while (matcher.find()) {
                int month = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
                if (month < 1 || month > 12) continue;
                dates.add(Integer.parseInt(matcher.group(1)) * 12 + month - 1);
            }
            if (dates.isEmpty()) continue;
            int end = period.contains("至今") || period.toLowerCase().contains("present") ? current
                    : dates.size() >= 2 ? Math.min(dates.get(1), current) : dates.getFirst();
            if (end > dates.getFirst()) intervals.add(new int[]{dates.getFirst(), end});
        }
        intervals.sort(Comparator.comparingInt(interval -> interval[0]));
        int total = 0, previousEnd = 0;
        for (int[] interval : intervals) {
            total += Math.max(0, interval[1] - Math.max(interval[0], previousEnd));
            previousEnd = Math.max(previousEnd, interval[1]);
        }
        return total / 12;
    }
}