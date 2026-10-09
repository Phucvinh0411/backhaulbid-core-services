package iuh.fit.se.contractservice.service.driveraccess;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Scope of the caller as forwarded by the gateway from a verified JWT. The gateway strips these headers
 * from client requests, so they only exist when the token carried them. Missing headers mean ACCOUNT.
 */
public record DriverScope(String authType, UUID tripId, UUID profileId, UUID assignmentId, Long assignmentVersion) {
    public static final String AUTH_TYPE = "X-Auth-Type";
    public static final String TRIP = "X-Driver-Trip-Id";
    public static final String PROFILE = "X-Driver-Profile-Id";
    public static final String ASSIGNMENT = "X-Assignment-Id";
    public static final String VERSION = "X-Assignment-Version";
    public static final String DRIVER_ASSIGNMENT = "DRIVER_ASSIGNMENT";
    static final DriverScope ACCOUNT = new DriverScope("ACCOUNT", null, null, null, null);

    public boolean isDriverSession() {
        return DRIVER_ASSIGNMENT.equals(authType);
    }

    /** Reads the scope of the current HTTP request; outside a request (jobs, tests) the caller is ACCOUNT. */
    public static DriverScope current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return ACCOUNT;
        return from(attributes.getRequest());
    }

    static DriverScope from(HttpServletRequest request) {
        String type = request.getHeader(AUTH_TYPE);
        if (!DRIVER_ASSIGNMENT.equals(type)) return ACCOUNT;
        try {
            return new DriverScope(type, UUID.fromString(request.getHeader(TRIP)), UUID.fromString(request.getHeader(PROFILE)),
                    UUID.fromString(request.getHeader(ASSIGNMENT)), Long.parseLong(request.getHeader(VERSION)));
        } catch (RuntimeException incomplete) {
            // A driver-session token without its full scope is treated as having no trip at all.
            return new DriverScope(type, null, null, null, null);
        }
    }
}
