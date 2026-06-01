package com.example.assistant.config;

import com.example.assistant.service.ProfileService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    ApplicationRunner initializeDefaultProfile(ProfileService profileService) {
        return args -> profileService.initializeDefaultTerms();
    }
}
