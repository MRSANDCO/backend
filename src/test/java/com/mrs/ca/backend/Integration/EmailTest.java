package com.mrs.ca.backend.Integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class EmailTest {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Test
    public void testSendEmail() {
        System.out.println("========== EMAIL SEND TEST ==========");
        if (mailSender == null) {
            System.out.println("WARNING: JavaMailSender is not configured in this profile. Skipping test.");
            System.out.println("=====================================");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("camrsandco@gmail.com");
            message.setTo("camrsandco@gmail.com"); // Send to self
            message.setSubject("Test Email from MRS & Co. Backend");
            message.setText("This is a test email to verify SMTP configuration.");
            
            mailSender.send(message);
            System.out.println("SUCCESS: Email sent successfully!");
        } catch (Exception e) {
            System.out.println("FAILURE: Email failed to send!");
            e.printStackTrace();
        }
        System.out.println("=====================================");
    }
}
