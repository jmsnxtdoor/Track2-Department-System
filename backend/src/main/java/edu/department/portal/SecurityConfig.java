package edu.department.portal;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.*;
import org.springframework.http.HttpStatus;
import java.util.*;
@Configuration public class SecurityConfig {
 static List<String> corsOrigins(String origin){
  List<String> allowedOrigins=Arrays.stream(origin.split(",")).map(String::trim).filter(value->!value.isEmpty()).toList();
  if(allowedOrigins.isEmpty())throw new IllegalArgumentException("At least one CORS origin must be configured.");
  if(allowedOrigins.stream().anyMatch(value->value.contains("*")))throw new IllegalArgumentException("Wildcard CORS origins are not allowed.");
  return allowedOrigins;
 }
 @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService users(UserRepo users){
  return username -> users.findByUsername(username)
   .map(account -> User.withUsername(account.username).password(account.passwordHash)
    .authorities("ROLE_" + account.role.name()).disabled(!account.enabled).build())
   .orElseThrow(() -> new UsernameNotFoundException("User not found."));
 }
 @Bean SecurityFilterChain filter(HttpSecurity http,@Value("${app.cors.origin}") String origin)throws Exception{
  List<String> allowedOrigins=corsOrigins(origin);
  http.csrf(c->c.disable()).cors(c->c.configurationSource(request->{CorsConfiguration x=new CorsConfiguration();x.setAllowedOrigins(allowedOrigins);x.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));x.setAllowedHeaders(List.of("Authorization","Content-Type"));return x;}))
  .authorizeHttpRequests(a->a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
  .requestMatchers("/api/health","/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**").permitAll()
  .requestMatchers(HttpMethod.GET,"/api/me").authenticated()
  .requestMatchers("/api/students/me/**").hasRole("STUDENT")
  .requestMatchers("/api/admin/**").hasRole("ADMIN")
  .requestMatchers("/api/finance/**").hasAnyRole("ADMIN","TREASURER")
  .requestMatchers("/api/attendance/**").hasAnyRole("ADMIN","OFFICER")
  .requestMatchers(HttpMethod.GET,"/api/students/*/ledger","/api/students/*/balance").hasAnyRole("ADMIN","TREASURER")
  .requestMatchers(HttpMethod.GET,"/api/reports/finance","/api/reports/funds").hasAnyRole("ADMIN","TREASURER")
  .requestMatchers(HttpMethod.GET,"/api/reports/attendance").hasAnyRole("ADMIN","OFFICER")
  .requestMatchers(HttpMethod.GET,"/api/students/**","/api/dashboard","/api/reports/students","/api/reports/summary").hasAnyRole("ADMIN","TREASURER","OFFICER")
  .requestMatchers("/api/students/**").hasRole("ADMIN")
  .anyRequest().denyAll()).httpBasic(b->b.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));return http.build();
 }
}
