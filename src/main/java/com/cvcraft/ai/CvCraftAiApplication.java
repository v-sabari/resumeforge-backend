package com.cvcraft.ai;

import com.cvcraft.ai.config.RazorpayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RazorpayProperties.class)
public class CvCraftAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CvCraftAiApplication.class, args);
    }
}
