package com.iras;

import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class AgentController {
    public record Start(long jobId, String request) {}
    public record Reply(String answer) {}
    private record Subscription(UUID id, long owner, SseEmitter emitter) {}
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();
    private final AgentTaskService tasks;
    private final AccountService accounts;
    public AgentController(AgentTaskService tasks, AccountService accounts) { this.tasks = tasks; this.accounts = accounts; }

    @PostMapping("/api/agent/tasks")
    ResponseEntity<?> create(@RequestBody Start input, Authentication authentication) {
        return ResponseEntity.accepted().body(ResumeController.success(tasks.create(accounts.require(authentication).id(), input.jobId(), input.request())));
    }
    @GetMapping("/api/agent/tasks/{id}")
    Map<String, Object> get(@PathVariable UUID id, Authentication authentication) { return ResumeController.success(tasks.get(id, accounts.require(authentication).id())); }
    @PostMapping("/api/agent/tasks/{id}/reply")
    Map<String, Object> reply(@PathVariable UUID id, @RequestBody Reply input, Authentication authentication) {
        return ResumeController.success(tasks.reply(id, accounts.require(authentication).id(), input.answer()));
    }
    @PostMapping("/api/agent/tasks/{id}/cancel")
    Map<String, Object> cancel(@PathVariable UUID id, Authentication authentication) { return ResumeController.success(tasks.cancel(id, accounts.require(authentication).id())); }
    @GetMapping(value = "/api/agent/tasks/{id}/events", produces = "text/event-stream")
    SseEmitter events(@PathVariable UUID id, Authentication authentication) {
        long owner = accounts.require(authentication).id();
        tasks.get(id, owner);
        var emitter = new SseEmitter(240_000L);
        var subscription = new Subscription(id, owner, emitter);
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> { subscriptions.remove(subscription); emitter.complete(); });
        emitter.onError(error -> subscriptions.remove(subscription));
        send(subscription);
        return emitter;
    }
    @EventListener
    public void changed(AgentTaskService.Changed event) {
        subscriptions.stream().filter(subscription -> subscription.id().equals(event.id())).forEach(this::send);
    }
    private void send(Subscription subscription) {
        try {
            var task = tasks.get(subscription.id(), subscription.owner());
            subscription.emitter().send(SseEmitter.event().name("progress").data(task));
            if (Set.of("COMPLETED", "FAILED", "CANCELLED", "WAITING").contains(task.get("state"))) subscription.emitter().complete();
        } catch (Exception error) {
            subscriptions.remove(subscription);
            subscription.emitter().complete();
        }
    }
    @GetMapping("/api/reports")
    Map<String, Object> reports(Authentication authentication) { return ResumeController.success(tasks.reports(accounts.require(authentication).id())); }
    @GetMapping("/api/reports/{id}")
    Map<String, Object> report(@PathVariable UUID id, Authentication authentication) { return ResumeController.success(tasks.report(id, accounts.require(authentication).id())); }
    @PostMapping("/api/reports/{id}/approve")
    Map<String, Object> approve(@PathVariable UUID id, Authentication authentication) { return ResumeController.success(tasks.approve(id, accounts.require(authentication).id())); }
}