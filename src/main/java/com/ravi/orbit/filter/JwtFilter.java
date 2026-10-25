package com.ravi.orbit.filter;

import com.ravi.orbit.entity.User;
import com.ravi.orbit.enums.ERole;
import com.ravi.orbit.enums.EStatus;
import com.ravi.orbit.exceptions.ExpiredTokenException;
import com.ravi.orbit.exceptions.InvalidTokenException;
import com.ravi.orbit.repository.UserRepository;
import com.ravi.orbit.repository.RoleRepository;
import com.ravi.orbit.utils.CommonMethods;
import com.ravi.orbit.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!token.contains(".")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Validate token (throws if expired/invalid)
            jwtUtil.validateToken(token);

            String username = jwtUtil.extractUsername(token);
            String role = jwtUtil.extractRole(token);

            if (CommonMethods.isEmpty(role)) {
                throw new InvalidTokenException("Token has no role");
            }

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new InvalidTokenException("User no longer exists"));
            if (!EStatus.ACTIVE.equals(user.getStatus())) {
                throw new InvalidTokenException("User account is not active");
            }
            if (!roleRepository.existsRoleByUserIdAndRoleTitleAndStatus(
                    user.getId(), ERole.valueOf(role), EStatus.ACTIVE)) {
                throw new InvalidTokenException("User no longer has this role");
            }

            SimpleGrantedAuthority authority =
                    new SimpleGrantedAuthority(role);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            List.of(authority)
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);

        } catch (ExpiredTokenException ex) {
            SecurityContextHolder.clearContext();
            sendUnauthorized(response, "Token has expired");
        } catch (InvalidTokenException ex) {
            SecurityContextHolder.clearContext();
            sendUnauthorized(response, "Invalid token");
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message)
            throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        response.getWriter().write("""
            {
              "status": 401,
              "error": "Unauthorized",
              "message": "%s"
            }
            """.formatted(message));
    }
}
