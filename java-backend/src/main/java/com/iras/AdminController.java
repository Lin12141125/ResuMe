package com.iras;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class AdminController {
    public record UserInput(String user_account, String password) {}
    public record DeleteInput(long user_id) {}
    private final AccountService accounts;
    private final ResumeService resumes;
    private final JdbcTemplate database;
    public AdminController(AccountService accounts, ResumeService resumes, JdbcTemplate database) {
        this.accounts = accounts; this.resumes = resumes; this.database = database;
    }
    private AccountPrincipal admin(Authentication authentication) {
        var principal = accounts.require(authentication);
        if (!principal.role().equals("ADMIN")) throw new ApiException(403, "Administrator required");
        return principal;
    }
    @GetMapping("/IRAS/FindAllUsers/")
    Map<String, Object> users(Authentication authentication) {
        admin(authentication);
        return ResumeController.success(database.queryForList("SELECT id AS user_id,account AS user_account,'招聘者' AS user_type FROM accounts WHERE role='USER' ORDER BY id"));
    }
    @PostMapping("/api/admin/users")
    Map<String, Object> create(@RequestBody UserInput input, Authentication authentication) {
        admin(authentication);
        return ResumeController.success(accounts.register(input.user_account(), input.password()));
    }
    @PostMapping("/IRAS/UserDelete/")
    Map<String, Object> delete(@RequestBody DeleteInput input, Authentication authentication) {
        admin(authentication);
        return ResumeController.success(Map.of("cleanupPending", resumes.deleteUser(input.user_id())));
    }
}