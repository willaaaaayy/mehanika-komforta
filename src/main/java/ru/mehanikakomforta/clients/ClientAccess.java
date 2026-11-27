package ru.mehanikakomforta.clients;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.mehanikakomforta.leads.AdminGuard;

public final class ClientAccess {

    private ClientAccess() {
    }

    public static boolean signedIn() {
        return AdminGuard.signedIn();
    }

    public static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "director";
        }
        return authentication.getName();
    }

    public static boolean canConvert() {
        return signedIn() && role().equals("director");
    }

    public static boolean canReadClients() {
        return canConvert();
    }

    private static String role() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "";
        }
        boolean director = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_DIRECTOR".equals(authority.getAuthority()));
        if (director) {
            return "director";
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", "").toLowerCase())
                .findFirst()
                .orElse("");
    }
}
