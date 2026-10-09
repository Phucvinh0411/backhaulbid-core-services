package iuh.fit.se.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/** Limit memory-backed multipart requests before DispatcherServlet parses any evidence. */
@Component @Order(-90) // Spring Security (-100) authenticates before this filter.
public class MultipartCapacityFilter extends OncePerRequestFilter {
    private final Semaphore capacity = new Semaphore(2);
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String type = request.getContentType();
        return type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("multipart/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!capacity.tryAcquire()) {
            response.setStatus(503); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Upload capacity reached; retry later\"}");
            return;
        }
        try { chain.doFilter(request, response); }
        finally { capacity.release(); }
    }
}
