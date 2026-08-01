package com.mrs.ca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO representing the response from Meta WhatsApp Cloud API.
 */
public class WhatsAppMessageResponse {
    
    @JsonProperty("messaging_product")
    private String messagingProduct;
    
    private List<Contact> contacts;
    private List<Message> messages;

    public WhatsAppMessageResponse() {}

    public String getMessagingProduct() {
        return messagingProduct;
    }

    public void setMessagingProduct(String messagingProduct) {
        this.messagingProduct = messagingProduct;
    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public void setContacts(List<Contact> contacts) {
        this.contacts = contacts;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public static class Contact {
        private String input;
        
        @JsonProperty("wa_id")
        private String waId;

        public Contact() {}

        public String getInput() {
            return input;
        }

        public void setInput(String input) {
            this.input = input;
        }

        public String getWaId() {
            return waId;
        }

        public void setWaId(String waId) {
            this.waId = waId;
        }
    }

    public static class Message {
        private String id;

        public Message() {}

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
