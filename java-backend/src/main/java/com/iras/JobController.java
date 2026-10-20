package com.iras;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService jobs;
    private final AccountService accounts;
    public JobController(JobService jobs, AccountService accounts) { this.jobs = jobs; this.accounts = accounts; }
    public record Input(String title, String description, JsonNode requirements) {}
    @GetMapping
    Map<String, Object> list(Authentication authentication) { return ResumeController.success(jobs.list(accounts.require(authentication).id())); }
    @GetMapping("/{id}")
    Map<String, Object> get(@PathVariable long id, Authentication authentication) { return ResumeController.success(jobs.get(id, accounts.require(authentication).id())); }
    @PostMapping
    Map<String, Object> create(@RequestBody Input input, Authentication authentication) {
        return ResumeController.success(jobs.create(accounts.require(authentication).id(), input.title(), input.description()));
    }
    @PutMapping("/{id}")
    Map<String, Object> update(@PathVariable long id, @RequestBody Input input, Authentication authentication) {
        return ResumeController.success(jobs.update(id, accounts.require(authentication).id(), input.title(), input.description(), input.requirements()));
    }
    @PostMapping("/{id}/confirm")
    Map<String, Object> confirm(@PathVariable long id, Authentication authentication) {
        return ResumeController.success(jobs.confirm(id, accounts.require(authentication).id()));
    }

    @DeleteMapping("/{id}")
    Map<String, Object> delete(@PathVariable long id, Authentication authentication) {
        jobs.delete(id, accounts.require(authentication).id());
        return ResumeController.success(Map.of("deleted", true));
    }
}