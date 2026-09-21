package com.sih.dataservice.auth.jwt;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, UserRepository userRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            Claims claims = jwtTokenService.parseAndValidateToken(token);

            if (claims != null && JwtTokenService.TYPE_ACCESS.equals(jwtTokenService.extractTokenType(claims))) {
                UUID userId = jwtTokenService.extractUserId(claims);
                int tokenVersion = jwtTokenService.extractTokenVersion(claims);

                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();

                    // Check account status and instant token revocation via token_version check (FR-AUTH-3, FR-AUTH-4)
                    if (user.getStatus() == UserStatus.ACTIVE && user.getTokenVersion() == tokenVersion) {
                        UserPrincipal principal = UserPrincipal.fromUser(user);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
