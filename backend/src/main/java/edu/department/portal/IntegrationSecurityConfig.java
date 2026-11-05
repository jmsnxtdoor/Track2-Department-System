package edu.department.portal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class IntegrationSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain integrationApiFilterChain(
            HttpSecurity http,
            @Value("${track2.integration.shared-api-key:}") String sharedApiKey) throws Exception {
        http.securityMatcher("/api/integration/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new SharedApiKeyFilter(sharedApiKey), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        (request, response, failure) -> response.sendError(HttpStatus.UNAUTHORIZED.value())));
        return http.build();
    }
}
