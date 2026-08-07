package az.catalog.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    private final AppProperties properties;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Universal Korporativ Kataloq Platforması API")
                        .version("1.0.0")
                        .description("""
                                White-label kataloq, kontent və e-ticarət platforması.

                                **Qruplar**
                                - `/api/public/**` — storefront (autentifikasiya tələb olunmur)
                                - `/api/customer/**` — müştəri kabineti (CUSTOMER token)
                                - `/api/admin/**` — idarəetmə paneli (ADMIN token)

                                **Dil:** bütün public endpoint-lər `Accept-Language` başlığını oxuyur (`az`, `en`, `ru`).
                                """)
                        .contact(new Contact().name("Platform komandası"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url(properties.getApiUrl()).description("API")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Login endpoint-indən qayıdan accessToken")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
