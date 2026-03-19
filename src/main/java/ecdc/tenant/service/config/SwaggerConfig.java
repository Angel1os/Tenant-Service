package ecdc.tenant.service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public OpenAPI customOpenAPI() {
        String tokenUrl = issuerUri + "/protocol/openid-connect/token";

        // Define scopes for password grant
        Scopes passwordScopes = new Scopes();
        passwordScopes.addString("openid", "OpenID Connect scope");
        passwordScopes.addString("profile", "User profile information");
        passwordScopes.addString("email", "User email address");

        // Define scopes for client credentials grant
        Scopes clientCredentialsScopes = new Scopes();
        clientCredentialsScopes.addString("openid", "OpenID Connect scope");

        return new OpenAPI()
                .info(new Info()
                        .title("Tenant Service API")
                        .description("The Tenant Service manages platform multitenancy by provisioning and operating " +
                                "a dedicated Tractus-X EDC Control Plane and Data Plane per tenant. It stores tenant " +
                                "identity and configuration (tenant key, participant DID/BPN, per-tenant namespace/DB, " +
                                "and gateway base URLs) and exposes endpoints to create, update, list, and view tenant " +
                                "status (with lifecycle states such as PROVISIONING, READY, STOPPED, DELETED, FAILED). " +
                                "Protected endpoints require OAuth2 authentication via Keycloak.")
                        .version("1.0")
                        .contact(new Contact()
                                .email("support@docexploit.com")
                                .name("Developer: DocExploit")
                        )
                        .license(new License()
                                .name("Tenant")
                        )
                )
                .addSecurityItem(new SecurityRequirement()
                        .addList("OAuth2")
                )
                .components(new Components()
                        .addSecuritySchemes("OAuth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("OAuth2 authentication with Keycloak. " +
                                        "Use 'Password' flow for username/password authentication or " +
                                        "'Client Credentials' for service-to-service authentication.")
                                .flows(new OAuthFlows()
                                        .password(new OAuthFlow()
                                                .tokenUrl(tokenUrl)
                                                .refreshUrl(tokenUrl)
                                                .scopes(passwordScopes)
                                        )
                                        .clientCredentials(new OAuthFlow()
                                                .tokenUrl(tokenUrl)
                                                .scopes(clientCredentialsScopes)
                                        )
                                )
                        )
                );
    }
}
