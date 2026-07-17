package com.mrs.ca.backend.Integration;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;

public class EmailSmtpTest {

    @Test
    public void testSendEmailStandalone() {
        System.out.println("========== STANDALONE SMTP TEST ==========");
        
        String mailUsername = null;
        String mailPassword = null;

        // Load credentials from .env file
        try {
            File envFile = new File(".env");
            if (envFile.exists()) {
                for (String line : Files.readAllLines(envFile.toPath())) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        int pos = line.indexOf("=");
                        if (pos > 0) {
                            String key = line.substring(0, pos).trim();
                            String val = line.substring(pos + 1).trim();
                            if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
                            if (val.startsWith("'") && val.endsWith("'")) val = val.substring(1, val.length() - 1);
                            
                            if ("MAIL_USERNAME".equals(key)) {
                                mailUsername = val;
                            } else if ("MAIL_PASSWORD".equals(key)) {
                                mailPassword = val;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to read .env file: " + e.getMessage());
        }

        if (mailUsername == null || mailUsername.isEmpty()) {
            System.out.println("FAILURE: MAIL_USERNAME not set in .env!");
            return;
        }

        System.out.println("Using Mail Username: " + mailUsername);
        System.out.println("Using Mail Password Length: " + (mailPassword != null ? mailPassword.length() : 0));

        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost("smtp.gmail.com");
            mailSender.setPort(587);
            mailSender.setUsername(mailUsername);
            mailSender.setPassword(mailPassword);

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(mailUsername); // Send to self
            message.setSubject("Test Standalone SMTP Email");
            message.setText("This is a direct SMTP test message from MRS & Co. standalone test.");

            mailSender.send(message);
            System.out.println("SUCCESS: Direct SMTP email sent successfully!");
        } catch (Exception e) {
            System.out.println("FAILURE: Direct SMTP email failed to send!");
            e.printStackTrace();
        }
        System.out.println("=========================================");
    }
}
