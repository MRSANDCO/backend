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

import com.mrs.ca.backend.Models.QueryResponse;
import com.mrs.ca.backend.Repositories.QueryResponseRepository;
import com.mrs.ca.backend.dto.QueryConversationDto;

@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    @Mock private QueryRepository queryRepository;
    @Mock private QueryResponseRepository queryResponseRepository;
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

    @Test
    void testAddClientResponse_Success() {
        Query query = new Query();
        query.setId("q_100");
        query.setSubject("Documents required");
        query.setTargetUser(targetUser);
        query.setStatus(Query.QueryStatus.OPEN);

        when(userRepository.findByUserId("client1")).thenReturn(Optional.of(targetUser));
        when(queryRepository.findById("q_100")).thenReturn(Optional.of(query));
        when(queryResponseRepository.save(any(QueryResponse.class))).thenAnswer(inv -> {
            QueryResponse r = inv.getArgument(0);
            r.setId("resp_1");
            return r;
        });
        when(queryRepository.save(any(Query.class))).thenAnswer(inv -> inv.getArgument(0));

        QueryResponse resp = queryService.addClientResponse("q_100", "client1", "I have uploaded the files.");

        assertThat(resp).isNotNull();
        assertThat(resp.getMessage()).isEqualTo("I have uploaded the files.");
        assertThat(resp.getSenderRole()).isEqualTo(QueryResponse.SenderRole.CLIENT);
        assertThat(resp.getSenderId()).isEqualTo("client1");
        assertThat(resp.getClientId()).isEqualTo("client1");
        assertThat(query.getStatus()).isEqualTo(Query.QueryStatus.CLIENT_RESPONDED);

        verify(queryRepository).save(query);
        verify(emailService).sendClientResponseNotification(eq(targetUser), eq(query), eq(resp));
    }

    @Test
    void testAddClientResponse_UnauthorizedUser_ThrowsSecurityException() {
        User otherUser = new User();
        otherUser.setId("db_id_999");
        otherUser.setUserId("client2");

        Query query = new Query();
        query.setId("q_100");
        query.setTargetUser(targetUser); // belongs to client1

        when(userRepository.findByUserId("client2")).thenReturn(Optional.of(otherUser));
        when(queryRepository.findById("q_100")).thenReturn(Optional.of(query));

        org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class, () -> {
            queryService.addClientResponse("q_100", "client2", "Unauthorized response");
        });

        verify(queryResponseRepository, never()).save(any());
        verify(emailService, never()).sendClientResponseNotification(any(), any(), any());
    }

    @Test
    void testAddClientResponse_EmptyMessage_ThrowsIllegalArgumentException() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            queryService.addClientResponse("q_100", "client1", "   ");
        });
    }

    @Test
    void testAddAdminResponse_Success() {
        Query query = new Query();
        query.setId("q_100");
        query.setSubject("Documents required");
        query.setTargetUser(targetUser);
        query.setStatus(Query.QueryStatus.CLIENT_RESPONDED);

        when(queryRepository.findById("q_100")).thenReturn(Optional.of(query));
        when(queryResponseRepository.save(any(QueryResponse.class))).thenAnswer(inv -> {
            QueryResponse r = inv.getArgument(0);
            r.setId("resp_admin_1");
            return r;
        });
        when(queryRepository.save(any(Query.class))).thenAnswer(inv -> inv.getArgument(0));

        QueryResponse resp = queryService.addAdminResponse("q_100", "admin", "Documents verified successfully.");

        assertThat(resp).isNotNull();
        assertThat(resp.getMessage()).isEqualTo("Documents verified successfully.");
        assertThat(resp.getSenderRole()).isEqualTo(QueryResponse.SenderRole.ADMIN);
        assertThat(query.getStatus()).isEqualTo(Query.QueryStatus.ADMIN_RESPONDED);

        verify(queryRepository).save(query);
    }

    @Test
    void testGetQueryConversation_ReturnsChronologicalResponses() {
        Query query = new Query();
        query.setId("q_100");
        query.setTargetUser(targetUser);

        QueryResponse r1 = new QueryResponse("q_100", "client1", "client1", "John", "c@e.com", QueryResponse.SenderRole.CLIENT, "Message 1");
        QueryResponse r2 = new QueryResponse("q_100", "client1", "admin", "Admin", null, QueryResponse.SenderRole.ADMIN, "Message 2");

        when(userRepository.findByUserId("client1")).thenReturn(Optional.of(targetUser));
        when(queryRepository.findById("q_100")).thenReturn(Optional.of(query));
        when(queryResponseRepository.findByQueryIdOrderByCreatedAtAsc("q_100")).thenReturn(java.util.List.of(r1, r2));

        QueryConversationDto conversation = queryService.getQueryConversation("q_100", "client1", false);

        assertThat(conversation).isNotNull();
        assertThat(conversation.getQuery().getId()).isEqualTo("q_100");
        assertThat(conversation.getResponses()).hasSize(2);
        assertThat(conversation.getResponses().get(0).getMessage()).isEqualTo("Message 1");
        assertThat(conversation.getResponses().get(1).getMessage()).isEqualTo("Message 2");
    }

    @Test
    void testDeleteQuery_CascadesResponses() {
        Query query = new Query();
        query.setId("q_100");

        when(queryRepository.findById("q_100")).thenReturn(Optional.of(query));

        queryService.deleteQuery("q_100");

        verify(queryResponseRepository).deleteByQueryId("q_100");
        verify(queryRepository).delete(query);
    }
}
