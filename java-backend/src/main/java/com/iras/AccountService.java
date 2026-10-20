package com.iras;

import java.nio.charset.StandardCharsets;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private final JdbcTemplate database;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    public AccountService(JdbcTemplate database, PasswordEncoder passwords) {
        this.database = database;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    public AccountPrincipal register(String account, String password) {
        return create(account, password, "USER");
    }

    AccountPrincipal createAdmin(String account, String password) {
        return create(account, password, "ADMIN");
    }

    private AccountPrincipal create(String account, String password, String role) {
        validate(account, password);
        if (password.length() < 10) throw new ApiException(400, "Password must contain at least 10 characters");
        try {
            Long id = database.queryForObject(
                        "INSERT INTO accounts(account,password_hash,role) VALUES (?,?,?) RETURNING id",
                        Long.class, account.strip(), passwords.encode(password), role);
                    return new AccountPrincipal(id, account.strip(), role);
        } catch (DuplicateKeyException error) {
            throw new ApiException(409, "Account already exists");
        }
    }

    public AccountPrincipal login(String account, String password, String role) {
        validate(account, password);
        var rows = database.queryForList("SELECT id,account,password_hash,role FROM accounts WHERE account=? AND role=?",
                account.strip(), role);
        String hash = rows.isEmpty() ? dummyHash : (String) rows.getFirst().get("password_hash");
        if (!passwords.matches(password, hash) || rows.isEmpty()) throw new ApiException(401, "Invalid credentials");
        var row = rows.getFirst();
        return new AccountPrincipal(((Number) row.get("id")).longValue(), (String) row.get("account"), role);
    }

    public AccountPrincipal require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new ApiException(401, "Sign in required");
        }
        Integer count = database.queryForObject("SELECT count(*) FROM accounts WHERE id=? AND role=?",
                Integer.class, principal.id(), principal.role());
        if (count == null || count == 0) throw new ApiException(401, "Account no longer exists");
        return principal;
    }

    private void validate(String account, String password) {
        if (account == null || account.isBlank() || account.strip().length() > 60
                || password == null || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(400, "Invalid account or password length");
        }
    }
}