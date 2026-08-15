package com.mrs.ca.backend.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class that reads Meta WhatsApp Cloud API credentials and endpoints from properties.
 */
@Configuration
public class WhatsAppConfig {

    @Value("${whatsapp.access-token:}")
    private String accessToken;

    @Value("${whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${whatsapp.api-version:v23.0}")
    private String apiVersion;

    @Value("${whatsapp.base-url:https://graph.facebook.com}")
    private String baseUrl;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${whatsapp.template-name:query_raised_notification}")
    private String templateName;

    @Value("${whatsapp.template-language:en}")
    private String templateLanguage;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getTemplateLanguage() {
        return templateLanguage;
    }
}
