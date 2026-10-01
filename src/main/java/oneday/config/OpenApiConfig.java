package oneday.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The API contract the apps are generated from ({@code docs/api/openapi.json}; see docs/08-flutter-integration.md).
 * Every operation needs the bearer access token unless listed as public in SecurityConfig; POSTs accept an
 * {@code Idempotency-Key} so retries on flaky networks are safe.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	static final String BEARER = "bearer";

	@Bean
	OpenAPI oneDayOpenApi() {
		return new OpenAPI()
			.info(new Info().title("OneDay API")
				.version("1")
				.description("Location-first social and dating. Errors are RFC 9457 problem details with a stable "
						+ "`code`; see docs/08-flutter-integration.md."))
			.components(new Components().addSecuritySchemes(BEARER,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}

	/**
	 * Stable, readable operation ids and one tag per controller: {@code TimeCapsuleController#seal} becomes
	 * operation {@code timeCapsuleSeal} under tag {@code TimeCapsule}, so generated clients read
	 * {@code TimeCapsuleApi.timeCapsuleSeal(...)} and adding a controller never renumbers existing ids.
	 */
	@Bean
	OperationCustomizer operationNames() {
		return (operation, handler) -> {
			String controller = handler.getBeanType().getSimpleName().replaceFirst("Controller$", "");
			String method = handler.getMethod().getName();
			operation.setOperationId(Character.toLowerCase(controller.charAt(0)) + controller.substring(1)
					+ Character.toUpperCase(method.charAt(0)) + method.substring(1));
			operation.setTags(List.of(controller));
			return operation;
		};
	}

	@Bean
	OpenApiCustomizer idempotencyHeader() {
		return api -> api.getPaths().values().forEach(path -> {
			if (path.getPost() != null) {
				path.getPost()
					.addParametersItem(new HeaderParameter().name("Idempotency-Key")
						.required(false)
						.description("Optional, up to 100 characters: a retry with the same key and body replays "
								+ "the first response for 24 h")
						.schema(new StringSchema().maxLength(100)));
			}
		});
	}
}
