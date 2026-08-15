package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Config.WhatsAppConfig;
import com.mrs.ca.backend.Models.Query;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.dto.WhatsAppMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    @Mock
    private WhatsAppConfig whatsAppConfig;

    @Mock
    private RestTemplate restTemplate;

    private WhatsAppService whatsAppService;

    @BeforeEach
    void setUp() {
        whatsAppService = new WhatsAppService(whatsAppConfig, restTemplate);
    }

    @Test
    void testNormalizePhoneNumber() {
        assertThat(whatsAppService.normalizePhoneNumber(null)).isEmpty();
        assertThat(whatsAppService.normalizePhoneNumber("")).isEmpty();
        assertThat(whatsAppService.normalizePhoneNumber("+91 98765-43210")).isEqualTo("919876543210");
        assertThat(whatsAppService.normalizePhoneNumber("919876543210")).isEqualTo("919876543210");
        assertThat(whatsAppService.normalizePhoneNumber("abc")).isEmpty();
    }

    @Test
    void testSendQueryNotification_NoPhone_Skipped() {
        User user = new User();
        user.setUserId("client1");
        user.setPhone(null);

        Query query = new Query();

        whatsAppService.sendQueryNotification(user, query);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void testSendQueryNotification_InvalidPhone_Skipped() {
        User user = new User();
        user.setUserId("client1");
        user.setPhone("   ");

        Query query = new Query();

        whatsAppService.sendQueryNotification(user, query);
        verifyNoInteractions(restTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSendQueryNotification_Success() {
        User user = new User();
        user.setUserId("client1");
        user.setFullName("John Doe");
        user.setPhone("+91 98765-43210");

        Query query = new Query();
        query.setId("query123");
        query.setSubject("Tax Audit Query");

        when(whatsAppConfig.getAccessToken()).thenReturn("dummy_access_token");
        when(whatsAppConfig.getPhoneNumberId()).thenReturn("dummy_phone_id");
        when(whatsAppConfig.getBaseUrl()).thenReturn("https://graph.facebook.com");
        when(whatsAppConfig.getApiVersion()).thenReturn("v25.0");
        when(whatsAppConfig.getTemplateName()).thenReturn("query_raised_notification");
        when(whatsAppConfig.getTemplateLanguage()).thenReturn("en");
        when(whatsAppConfig.getFrontendUrl()).thenReturn("https://mrsandco.in");

        whatsAppService.sendQueryNotification(user, query);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("https://graph.facebook.com/v25.0/dummy_phone_id/messages"),
                captor.capture(),
                eq(String.class)
        );

        HttpEntity<Map<String, Object>> entity = captor.getValue();
        assertThat(entity).isNotNull();
        
        HttpHeaders headers = entity.getHeaders();
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer dummy_access_token");

        Map<String, Object> payload = entity.getBody();
        assertThat(payload).isNotNull();
        assertThat(payload.get("messaging_product")).isEqualTo("whatsapp");
        assertThat(payload.get("to")).isEqualTo("919876543210");
        assertThat(payload.get("type")).isEqualTo("template");

        Map<String, Object> template = (Map<String, Object>) payload.get("template");
        assertThat(template).isNotNull();
        assertThat(template.get("name")).isEqualTo("query_raised_notification");

        Map<String, Object> language = (Map<String, Object>) template.get("language");
        assertThat(language.get("code")).isEqualTo("en");

        List<Map<String, Object>> components = (List<Map<String, Object>>) template.get("components");
        assertThat(components).hasSize(1);
        
        // Body component (Client Name {{1}}, Query Subject {{2}})
        Map<String, Object> bodyComponent = components.get(0);
        assertThat(bodyComponent.get("type")).isEqualTo("body");
        
        List<Map<String, Object>> bodyParams = (List<Map<String, Object>>) bodyComponent.get("parameters");
        assertThat(bodyParams).hasSize(2);
        assertThat(bodyParams.get(0).get("type")).isEqualTo("text");
        assertThat(bodyParams.get(0).get("text")).isEqualTo("John Doe");
        assertThat(bodyParams.get(1).get("type")).isEqualTo("text");
        assertThat(bodyParams.get(1).get("text")).isEqualTo("Tax Audit Query");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSendTextMessage_Success() {
        when(whatsAppConfig.getAccessToken()).thenReturn("dummy_access_token");
        when(whatsAppConfig.getPhoneNumberId()).thenReturn("dummy_phone_id");
        when(whatsAppConfig.getBaseUrl()).thenReturn("https://graph.facebook.com");
        when(whatsAppConfig.getApiVersion()).thenReturn("v23.0");

        WhatsAppMessageResponse mockResponse = new WhatsAppMessageResponse();
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(WhatsAppMessageResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        WhatsAppMessageResponse response = whatsAppService.sendTextMessage("919876543210", "Hello there");
        assertThat(response).isSameAs(mockResponse);

        verify(restTemplate).postForEntity(
                eq("https://graph.facebook.com/v23.0/dummy_phone_id/messages"),
                any(HttpEntity.class),
                eq(WhatsAppMessageResponse.class)
        );
    }
}
