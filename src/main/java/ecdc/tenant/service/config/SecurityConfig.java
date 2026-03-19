package ecdc.tenant.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - no authentication required
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/swagger-ui/index.html",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/api-docs",
                                "/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/webjars/springfox-swagger-ui/**",
                                "/graphiql",
                                "/graphql",
                                "/actuator/**",
                                "/ws/**"
                        ).permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }

    /**
     * JWT decoder (blocking) used by WebSocket interceptors and by the resource server.
     */
    @Bean
    public JwtDecoder jwtDecoderSync(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    /**
     * Converts JWT tokens to Spring Security authentication with roles.
     * Extracts roles from Keycloak's token structure:
     * - Realm roles: realm_access.roles
     * - Client roles: resource_access.{client-id}.roles
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    /**
     * Extracts authorities (roles) from JWT token.
     * Keycloak provides roles in two places:
     * 1. Realm roles: realm_access.roles
     * 2. Client roles: resource_access.{client-id}.roles
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(org.springframework.security.oauth2.jwt.Jwt jwt) {
        // Extract realm roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        List<String> realmRoles = List.of();
        if (realmAccess != null && realmAccess.get("roles") instanceof List) {
            realmRoles = (List<String>) realmAccess.get("roles");
        }

        // Extract client roles (for tenant-service client)
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        List<String> clientRoles = List.of();
        if (resourceAccess != null) {
            Object clientAccessObj = resourceAccess.get("tenant-service");
            if (clientAccessObj instanceof Map) {
                Map<String, Object> clientAccess = (Map<String, Object>) clientAccessObj;
                Object rolesObj = clientAccess.get("roles");
                if (rolesObj instanceof List) {
                    clientRoles = (List<String>) rolesObj;
                }
            }
        }

        // Combine realm and client roles, prefix with ROLE_ for Spring Security
        return Stream.concat(realmRoles.stream(), clientRoles.stream())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}
