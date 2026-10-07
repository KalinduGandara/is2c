package com.is2c.hrm.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** NFR-007 OpenAPI metadata; the spec is served only when hrm.api-docs=true (springdoc.* in application.yml). */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI hrmOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Premium HRM API").version("v1")
                        .description("HR, attendance, roster, leave and payroll. Errors are RFC 9457 problem+json."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
