package ua.edu.duikt.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri
    ) {
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

            if (issuerUri != null && !issuerUri.isBlank()) {
                OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
                jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer));
            }

            return jwtDecoder;
        }

        if (issuerUri != null && !issuerUri.isBlank()) {
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }

        throw new IllegalStateException("Не задано issuer-uri або jwk-set-uri для JWT decoder");
    }
}
