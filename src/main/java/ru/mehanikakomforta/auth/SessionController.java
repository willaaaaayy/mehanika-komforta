package ru.mehanikakomforta.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.mehanikakomforta.CrmProperties;
import ru.mehanikakomforta.SecretMatch;
import ru.mehanikakomforta.leads.AdminGuard;
import ru.mehanikakomforta.leads.ApiError;

import java.util.List;

@RestController
public class SessionController {

    private final CrmProperties properties;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public SessionController(CrmProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/session")
    public ResponseEntity<Void> current() {
        if (AdminGuard.signedIn()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/api/session")
    public ResponseEntity<?> open(
            @RequestBody(required = false) PasswordRequest request,
            HttpServletRequest http,
            HttpServletResponse response
    ) {
        String password = request == null ? null : request.password();
        if (!SecretMatch.same(properties.adminPassword(), password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("password"));
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                "owner",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        if (http.getSession(false) != null) {
            http.changeSessionId();
        }
        contexts.saveContext(context, http, response);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/session")
    public ResponseEntity<Void> close(HttpServletRequest http) {
        SecurityContextHolder.clearContext();
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    public record PasswordRequest(String password) {
    }
}
