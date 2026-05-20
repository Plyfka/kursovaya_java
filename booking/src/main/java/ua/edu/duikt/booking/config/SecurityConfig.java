package ua.edu.duikt.booking.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;
import ua.edu.duikt.booking.entity.User;
import ua.edu.duikt.booking.entity.UserStatus;
import ua.edu.duikt.booking.repository.UserRepository;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository userRepository) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/me/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/me/**").hasAnyRole("USER", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/classrooms/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/reservations").hasAnyRole("USER", "ADMIN")

                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/buildings/**").hasRole("ADMIN")
                        .requestMatchers("/api/classroom-types/**").hasRole("ADMIN")
                        .requestMatchers("/api/equipment/**").hasRole("ADMIN")
                        .requestMatchers("/api/reservations/**").hasRole("ADMIN")
                        .requestMatchers("/api/classrooms/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
                )
                .addFilterAfter(blockedUserFilter(userRepository), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private OncePerRequestFilter blockedUserFilter(UserRepository userRepository) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                Object authenticationObject = SecurityContextHolder.getContext().getAuthentication();

                if (!(authenticationObject instanceof AbstractAuthenticationToken authentication)
                        || !(authentication.getPrincipal() instanceof Jwt jwt)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String email = resolveEmail(jwt);
                if (email == null || email.isBlank()) {
                    filterChain.doFilter(request, response);
                    return;
                }

                User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
                if (user != null && user.getStatus() == UserStatus.BLOCKED) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Користувача заблоковано");
                    return;
                }

                filterChain.doFilter(request, response);
            }
        };
    }

    private String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        return email;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || realmAccess.get("roles") == null) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");

        return roles.stream()
                .map(role -> role.toUpperCase(Locale.ROOT))
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
