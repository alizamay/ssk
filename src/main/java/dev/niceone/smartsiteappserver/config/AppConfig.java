package dev.niceone.smartsiteappserver.config;

import dev.niceone.smartsiteappserver.payload.MyHomeResponse;
import dev.niceone.smartsiteappserver.utils.SimpleCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    @Bean
    public SimpleCache<Long, MyHomeResponse> myHomeCache() {
        return new SimpleCache<>();
    }
}

