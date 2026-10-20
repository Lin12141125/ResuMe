package com.iras;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component
@ConditionalOnProperty(name = "iras.admin.create", havingValue = "true")
public class AdminCommand implements ApplicationRunner {
    private final AccountService accounts;
    private final ConfigurableApplicationContext context;
    public AdminCommand(AccountService accounts, ConfigurableApplicationContext context) { this.accounts = accounts; this.context = context; }
    @Override
    public void run(ApplicationArguments arguments) {
        try {
            var console = System.console();
            if (console == null) throw new IllegalStateException("Run the admin command in an interactive terminal");
            String name = console.readLine("New administrator account: ");
            char[] password = console.readPassword("Password (at least 10 characters): ");
            if (password == null) throw new IllegalStateException("Password input cancelled");
            try { accounts.createAdmin(name, new String(password)); }
            finally { Arrays.fill(password, '\0'); }
            console.printf("Administrator created. No existing account was changed.%n");
        } finally { context.close(); }
    }
}