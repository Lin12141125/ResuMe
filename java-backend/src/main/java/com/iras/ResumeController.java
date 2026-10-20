package com.iras;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ResumeController {
    private final AccountService accounts;
    private final ResumeService resumes;
    private final StatisticsService statistics;

    public ResumeController(AccountService accounts, ResumeService resumes, StatisticsService statistics) {
        this.accounts = accounts;
        this.resumes = resumes;
        this.statistics = statistics;
    }

    public static Map<String, Object> success(Object data) { return Map.of("msg", "success", "code", 200, "data", data); }

    @PostMapping({"/IRAS/ResumeUpload2Interpret/", "/api/resumes"})
    Map<String, Object> upload(@RequestParam("file") List<MultipartFile> files,
            @RequestParam(name = "user_id", required = false) Long owner, Authentication authentication) {
        var user = accounts.require(authentication);
        if (owner != null && owner != user.id()) throw new ApiException(403, "Forbidden owner");
        return success(resumes.upload(user, files));
    }

    @GetMapping({"/IRAS/FindUserResumes/", "/api/resumes"})
    Map<String, Object> list(@RequestParam(name = "user_id", required = false) Long owner, Authentication authentication) {
        return success(resumes.list(accounts.require(authentication), owner));
    }

    @GetMapping("/IRAS/SingleResumePresent/")
    Map<String, Object> detail(@RequestParam("resumecode") long id, Authentication authentication) {
        return success(resumes.detail(id, accounts.require(authentication)));
    }

    @GetMapping("/api/resumes/{id}/evidence")
    Map<String, Object> evidence(@PathVariable long id, Authentication authentication) {
        return success(resumes.evidence(id, accounts.require(authentication)));
    }

    public record DeleteRequest(List<Long> code_list) {}

    @GetMapping("/IRAS/ResumeStatistic/")
    Map<String, Object> statistics(@RequestParam(name = "user_id", required = false) Long owner,
            @RequestParam(name = "code_list", required = false) List<Long> ids, Authentication authentication) {
        return success(statistics.calculate(accounts.require(authentication), owner, ids));
    }

    @PutMapping("/api/resumes/{id}/extraction")
    Map<String, Object> correct(@PathVariable long id, @RequestBody com.fasterxml.jackson.databind.JsonNode input, Authentication authentication) {
        return success(resumes.correct(id, input, accounts.require(authentication)));
    }

    @PostMapping("/IRAS/ResumeDelete/")
    Map<String, Object> delete(@RequestBody DeleteRequest input, Authentication authentication) {
        return success(Map.of("cleanupPending", resumes.delete(input.code_list(), accounts.require(authentication))));
    }
}