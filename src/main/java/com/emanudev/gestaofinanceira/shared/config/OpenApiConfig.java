package com.emanudev.gestaofinanceira.shared.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "API de Gestão Financeira Corporativa & Simulação de Risco (HPC)",
                version = "v1",
                description = "API RESTful para gestão de contas a pagar/receber, conciliação e baixa com split automático, trilha de auditoria imutável, relatórios executivos e simulação estocástica de Monte Carlo.",
                contact = @Contact(name = "Equipe de Arquitetura - Gestão Financeira")
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token JWT (HS256) obtido no endpoint POST /api/v1/auth/login"
)
public class OpenApiConfig {
}
