package Email_backend.Email_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.Collections;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow all origins matching any pattern
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        
        // Allow common HTTP headers + custom headers
        config.setAllowedHeaders(Arrays.asList(
            "Origin", 
            "Content-Type", 
            "Accept", 
            "Authorization", 
            "X-Requested-With", 
            "X-Internal-Call",
            "Access-Control-Allow-Origin",
            "X-Email",
            "X-Password"
        ));
        
        // Allow standard HTTP methods
        config.setAllowedMethods(Arrays.asList(
            "GET", 
            "POST", 
            "PUT", 
            "DELETE", 
            "OPTIONS", 
            "PATCH"
        ));
        
        // Allow credentials (necessary when using session / Authorization headers cross-origin)
        config.setAllowCredentials(true);
        
        // Cache preflight request response for 1 hour (3600 seconds)
        config.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
