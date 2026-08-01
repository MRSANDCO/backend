package com.mrs.ca.backend.exception;

/**
 * Custom runtime exception to handle failure cases in Meta WhatsApp integration.
 */
public class WhatsAppException extends RuntimeException {
    
    public WhatsAppException(String message) {
        super(message);
    }

    public WhatsAppException(String message, Throwable cause) {
        super(message, cause);
    }
}
