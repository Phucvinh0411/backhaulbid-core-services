package iuh.fit.se.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    // Streaming photos and error responses dispatch again after the stateless context is cleared.
    // Rebuild the same gateway-authenticated scope instead of permitting anonymous dispatches.
    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Lấy trực tiếp thông tin từ Header do Gateway truyền xuống
            String userId = request.getHeader("X-User-Id");
            String role = request.getHeader("X-User-Role");

            if (StringUtils.hasText(userId) && StringUtils.hasText(role)) {
                // Thêm tiền tố ROLE_ để Spring Security nhận diện đúng với @PreAuthorize
                // A code-login driver session is not a DRIVER account: it gets its own authority so no
                // account endpoint guarded by hasRole('DRIVER') accepts it.
                boolean driverSession = "DRIVER_ASSIGNMENT".equals(request.getHeader("X-Auth-Type"));
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority(driverSession ? "ROLE_DRIVER_SESSION" : "ROLE_" + role);

                // Nạp vào SecurityContext
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, Collections.singletonList(authority));
                
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }
}
