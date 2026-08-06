package com.mrs.ca.backend.Services;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.mrs.ca.backend.Models.Query;
import com.mrs.ca.backend.Models.User;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private GridFsTemplate gridFsTemplate;

    @Mock
    private GridFsOperations gridFsOperations;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setup() throws Exception {
        setField(emailService, "resendApiKey", "re_dummy_key");
        setField(emailService, "fromEmail", "onboarding@resend.dev");
        setField(emailService, "fromName", "MRS & Co.");
        setField(emailService, "frontendUrl", "http://localhost:3000");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testGetAttachmentBase64_Success() throws Exception {
        String mockGridFsId = new ObjectId().toHexString();
        byte[] mockBytes = "Hello Attachment File Content".getBytes();

        GridFSFile mockGridFSFile = mock(GridFSFile.class);
        GridFsResource mockResource = mock(GridFsResource.class);

        when(gridFsTemplate.findOne(any())).thenReturn(mockGridFSFile);
        when(gridFsOperations.getResource(mockGridFSFile)).thenReturn(mockResource);
        when(mockResource.getInputStream()).thenReturn(new ByteArrayInputStream(mockBytes));

        Method method = EmailService.class.getDeclaredMethod("getAttachmentBase64", String.class);
        method.setAccessible(true);
        String base64Result = (String) method.invoke(emailService, mockGridFsId);

        String expectedBase64 = java.util.Base64.getEncoder().encodeToString(mockBytes);
        assertThat(base64Result).isEqualTo(expectedBase64);

        verify(gridFsTemplate).findOne(any());
        verify(gridFsOperations).getResource(mockGridFSFile);
    }

    @Test
    void testBuildHtmlEmail_ContainsNoLoginAttachmentButton() throws Exception {
        User user = new User("client01", "password", "Jane Doe", "jane@example.com", "1234567890");
        Query query = new Query();
        query.setId("query123");
        query.setSubject("Test Subject");
        query.setMessageText("Test Query Description");
        query.setType(Query.QueryType.PDF);
        query.setFileName("contract.pdf");
        query.setGridFsId("someGridFsId");

        Method method = EmailService.class.getDeclaredMethod("buildHtmlEmail", User.class, Query.class);
        method.setAccessible(true);
        String html = (String) method.invoke(emailService, user, query);

        assertThat(html).contains("Attachment (sent with email)");
        assertThat(html).contains("contract.pdf");
        assertThat(html).doesNotContain("Login to Download Attachment");
        assertThat(html).doesNotContain("Please log in to your dashboard to download the attachment securely");
    }
}
