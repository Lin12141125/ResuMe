package com.iras;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AccountService accounts;
    private final CsrfTokenRepository csrf;

    public AuthController(AccountService accounts, CsrfTokenRepository csrf) {
        this.accounts = accounts;
        this.csrf = csrf;
    }

    public record Credentials(String account, String user_account, String admin_account, String password) {
        String username() { return account != null ? account : user_account != null ? user_account : admin_account; }
    }

    @PostMapping({"/api/auth/register", "/IRAS/UserRegister/"})
    Map<String, Object> register(@RequestBody Credentials input, HttpServletRequest request, HttpServletResponse response) {
        return establish(accounts.register(input.username(), input.password()), request, response);
    }

    @PostMapping({"/api/auth/login", "/IRAS/UserLogin/"})
    Map<String, Object> login(@RequestBody Credentials input, HttpServletRequest request, HttpServletResponse response) {
        return establish(accounts.login(input.username(), input.password(), "USER"), request, response);
    }

    @PostMapping("/IRAS/AdminLogin/")
    Map<String, Object> adminLogin(@RequestBody Credentials input, HttpServletRequest request, HttpServletResponse response) {
        return establish(accounts.login(input.username(), input.password(), "ADMIN"), request, response);
    }

    @GetMapping("/api/auth/me")
    Map<String, Object> me(Authentication authentication) {
        return Map.of("msg", "success", "code", 200, "data", accounts.require(authentication));
    }

    @PostMapping("/api/auth/logout")
    Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        csrf.saveToken(null, request, response);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return Map.of("msg", "success", "code", 200);
    }

    private Map<String, Object> establish(AccountPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        request.getSession();
        request.changeSessionId();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        csrf.saveToken(null, request, response);
        return Map.of("msg", "success", "code", 200, "token", "session",
                principal.role().equals("ADMIN") ? "admin_id" : "user_id", principal.id(), "data", principal);
    }
}