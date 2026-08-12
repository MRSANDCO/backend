package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.Query;
import com.mrs.ca.backend.Models.User;
import com.mrs.ca.backend.Repositories.QueryRepository;
import com.mrs.ca.backend.Repositories.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    @Mock private QueryRepository queryRepository;
    @Mock private UserRepository userRepository;
    @Mock private GridFsTemplate gridFsTemplate;
    @Mock private GridFsOperations gridFsOperations;
    @Mock private EmailService emailService;
    @Mock private WhatsAppService whatsAppService;

    @InjectMocks
    private QueryService queryService;

    private User targetUser;

    @BeforeEach
    void setUp() throws Exception {
        targetUser = new User();
        targetUser.setId("db_id_123");
        targetUser.setUserId("client1");
        targetUser.setFullName("John Doe");
        targetUser.setPhone("919876543210");
        targetUser.setEmail("client1@example.com");

        setField(queryService, "adminUsername", "admin");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testRaiseTextQuery_SendsEmailAndWhatsApp() {
        when(userRepository.findByUserId("client1")).thenReturn(Optional.of(targetUser));
        when(queryRepository.save(any(Query.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Query result = queryService.raiseTextQuery("client1", "Tax Query", "Please upload document");

        assertThat(result).isNotNull();
        assertThat(result.getSubject()).isEqualTo("Tax Query");
        assertThat(result.getMessageText()).isEqualTo("Please upload document");
        assertThat(result.getType()).isEqualTo(Query.QueryType.TEXT);

        verify(emailService).sendQueryNotification(eq(targetUser), eq(result));
        verify(whatsAppService).sendQueryNotification(eq(targetUser), eq(result));
    }

    @Test
    void testRaisePdfQuery_SendsEmailAndWhatsApp() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "dummy pdf content".getBytes());
        ObjectId gridFsId = new ObjectId();

        when(userRepository.findByUserId("client1")).thenReturn(Optional.of(targetUser));
        when(gridFsTemplate.store(any(InputStream.class), eq("invoice.pdf"), eq("application/pdf"))).thenReturn(gridFsId);
        when(queryRepository.save(any(Query.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Query result = queryService.raisePdfQuery("client1", "Invoice Issue", file);

        assertThat(result).isNotNull();
        assertThat(result.getSubject()).isEqualTo("Invoice Issue");
        assertThat(result.getType()).isEqualTo(Query.QueryType.PDF);
        assertThat(result.getGridFsId()).isEqualTo(gridFsId.toHexString());
        assertThat(result.getFileName()).isEqualTo("invoice.pdf");

        verify(emailService).sendQueryNotification(eq(targetUser), eq(result));
        verify(whatsAppService).sendQueryNotification(eq(targetUser), eq(result));
    }

    @Test
    void testRaiseQueryWithAttachment_SendsEmailAndWhatsApp() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", "dummy image content".getBytes());
        ObjectId gridFsId = new ObjectId();

        when(userRepository.findByUserId("client1")).thenReturn(Optional.of(targetUser));
        when(gridFsTemplate.store(any(InputStream.class), eq("receipt.png"), eq("image/png"))).thenReturn(gridFsId);
        when(queryRepository.save(any(Query.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Query result = queryService.raiseQueryWithAttachment("client1", "Receipt Needed", "Upload the receipt", file);

        assertThat(result).isNotNull();
        assertThat(result.getSubject()).isEqualTo("Receipt Needed");
        assertThat(result.getMessageText()).isEqualTo("Upload the receipt");
        assertThat(result.getType()).isEqualTo(Query.QueryType.PDF);
        assertThat(result.getGridFsId()).isEqualTo(gridFsId.toHexString());
        assertThat(result.getFileName()).isEqualTo("receipt.png");

        verify(emailService).sendQueryNotification(eq(targetUser), eq(result));
        verify(whatsAppService).sendQueryNotification(eq(targetUser), eq(result));
    }
}
