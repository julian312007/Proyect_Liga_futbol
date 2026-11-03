package co.edu.uniajc.liga.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la documentación OpenAPI y esquema de seguridad Bearer (JWT).
 *
 * <p>Los grupos de Swagger por módulo (autenticación, catálogos, equipos y jugadores,
 * partidos, estadísticas) se declaran en {@code application.yaml}, bajo
 * {@code springdoc.group-configs}.
 *
 * <p>El esquema {@code bearerAuth} habilita el botón <em>Authorize</em> de Swagger UI. Se aplica a
 * todas las operaciones; en la Fase 1 los endpoints públicos de registro y login deben
 * marcarse con {@code @SecurityRequirements} vacío para que no pidan token.
 */
@Configuration
public class OpenApiConfig {

    static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    OpenAPI ligaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API del Sistema de Gestión de una Liga de Fútbol")
                        .description("Gestión de ligas, equipos, jugadores, partidos, goles y tarjetas, "
                                + "con tabla de posiciones y estadísticas. Proyecto de Santiago Panesso "
                                + "y Julián Valencia (Universidad Antonio José Camacho, Cali).")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
    }
}
