package com.iras;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {
        public record Filters(Integer minWorkYears, String degree, List<String> skills) {}
    private final JdbcTemplate database;
    private final AiService ai;
    public SearchService(JdbcTemplate database, AiService ai) { this.database = database; this.ai = ai; }

    public List<Map<String, Object>> search(long owner, String query) {
                return search(owner, query, null);
        }

        public List<Map<String, Object>> search(long owner, String query, Filters filters) {
        if (query == null || query.isBlank() || query.length() > 1000) throw new ApiException(400, "Invalid search query");
                if (filters != null && ((filters.minWorkYears() != null && (filters.minWorkYears() < 0 || filters.minWorkYears() > 80))
                                || (filters.degree() != null && filters.degree().length() > 60)
                                || (filters.skills() != null && (filters.skills().size() > 20 || filters.skills().stream()
                                        .anyMatch(skill -> skill == null || skill.isBlank() || skill.length() > 100)))))
                        throw new ApiException(400, "Invalid search filters");
        String vector = ai.vectorLiteral(ai.embed(query));
        var tokens = java.util.Arrays.stream(query.toLowerCase(java.util.Locale.ROOT).split("[\\s,，;；]+"))
                .filter(token -> !token.isBlank()).limit(12).toList();
        if (tokens.isEmpty()) throw new ApiException(400, "Search query must contain a term");
        String keyword = String.join(" OR ", java.util.Collections.nCopies(tokens.size(), "position(? in lower(e.content))>0"));
        var parameters = new java.util.ArrayList<Object>();
        parameters.add(vector);
        parameters.addAll(tokens);
        parameters.add(vector);
        parameters.add(owner);
                String conditions = "";
                if (filters != null) {
                        if (filters.minWorkYears() != null) { conditions += " AND r.work_years>=?"; parameters.add(filters.minWorkYears()); }
                        if (filters.degree() != null && !filters.degree().isBlank()) {
                                conditions += " AND lower(r.extraction->>'最高学历')=lower(?)"; parameters.add(filters.degree().strip());
                        }
                        if (filters.skills() != null) for (String skill : filters.skills()) {
                                conditions += " AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(r.extraction->'技能专长') AS skill(value) WHERE position(lower(?) in lower(skill.value))>0)";
                                parameters.add(skill.strip());
                        }
                }
                var fragments = database.queryForList("WITH ranked AS (SELECT r.id AS resume_id,r.name,r.version,e.id AS evidence_id,e.page,e.paragraph,e.content,e.source, "
                + "(1-(e.embedding <=> CAST(? AS public.vector))) + CASE WHEN " + keyword + " THEN 0.2 ELSE 0 END AS retrieval_score, "
                        + "row_number() OVER(PARTITION BY r.id ORDER BY e.embedding <=> CAST(? AS public.vector)) AS fragment_rank "
                                                + "FROM evidence e JOIN resumes r ON r.id=e.resume_id AND e.version=r.version WHERE r.owner_id=?" + conditions + ") "
                        + "SELECT * FROM ranked WHERE fragment_rank<=2 ORDER BY retrieval_score DESC LIMIT 12", parameters.toArray());
                var candidates = new java.util.LinkedHashMap<Long, Map<String, Object>>();
                for (var fragment : fragments) {
                        long id = ((Number) fragment.get("resume_id")).longValue();
                        var candidate = candidates.computeIfAbsent(id, ignored -> {
                                var row = new java.util.LinkedHashMap<String, Object>();
                                row.put("resume_id", id);
                                row.put("name", fragment.get("name"));
                                row.put("version", fragment.get("version"));
                                row.put("evidence", new java.util.ArrayList<Map<String, Object>>());
                                return row;
                        });
                        @SuppressWarnings("unchecked")
                        var evidence = (List<Map<String, Object>>) candidate.get("evidence");
                        evidence.add(fragment);
                }
                return candidates.values().stream().limit(6).toList();
    }
}