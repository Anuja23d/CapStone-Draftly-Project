package org.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.client.GmailClient;
import org.entity.Email;
import org.entity.User;
import org.repository.EmailRepository;
import org.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class EmailService {

    @Autowired
    private GmailClient gmailClient;

    @Autowired
    private UserRepository userRepository;


    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private GoogleOAuthTokenService googleOAuthTokenService;

    private ObjectMapper mapper = new ObjectMapper();

    public List<Email> fetchInboxEmails(User user, int maxResults) throws Exception {
        String query = "in:inbox";
        return fetchEmailsByQuery(user, query, maxResults, "INBOX");
    }

    public List<Email> fetchUnreadInboxEmails(User user, int maxResults) throws Exception {
        String query = "in:inbox is:unread";
        return fetchEmailsByQuery(user, query, maxResults, "INBOX");
    }

    private List<Email> fetchEmailsByQuery(User user, String query, int maxResults, String label) throws Exception {
        String accessToken = user.getAccessToken();
        try {
            return fetchEmailsByQueryInternal(user, accessToken, query, maxResults, label);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401 && user.getRefreshToken() != null && !user.getRefreshToken().isBlank()) {
                String newAccessToken = googleOAuthTokenService.refreshAccessToken(user.getRefreshToken());
                user.setAccessToken(newAccessToken);
                userRepository.save(user);
                return fetchEmailsByQueryInternal(user, newAccessToken, query, maxResults, label);
            }
            throw e;
        }
    }

    private List<Email> fetchEmailsByQueryInternal(User user, String accessToken, String query, int maxResults, String label) throws Exception {
        String listJson = gmailClient.listMessages(accessToken, query, maxResults);
        JsonNode root = mapper.readTree(listJson);
        List<Email> out = new ArrayList<>();

        if (root == null || root.get("messages") == null) return out;

        for (JsonNode msg : root.get("messages")) {
            String messageId = msg.get("id").asText();

            // idempotent upsert per user+messageId
            Email email = emailRepository.findByUserAndMessageId(user, messageId).orElse(new Email());
            email.setUser(user);
            email.setMessageId(messageId);

            String fullJson = gmailClient.getMessageDetails(messageId, accessToken);
            JsonNode full = mapper.readTree(fullJson);

            email.setThreadId(Optional.ofNullable(full.get("threadId")).map(JsonNode::asText).orElse(null));
            email.setSnippet(Optional.ofNullable(full.get("snippet")).map(JsonNode::asText).orElse(null));
            email.setLabel(label);

            Email parsed = parseEmail(full);
            email.setSubject(parsed.getSubject());
            email.setSender(parsed.getSender());
            email.setBody(parsed.getBody());

            out.add(emailRepository.save(email));
        }

        return out;
    }



    private Email parseEmail(JsonNode json) {

        Email email = new Email();

        JsonNode payload = json.get("payload");

        // Headers
        for (JsonNode header : payload.get("headers")) {

            String name = header.get("name").asText();
            String value = header.get("value").asText();

            if ("Subject".equalsIgnoreCase(name)) {
                email.setSubject(value);
            }

            if ("From".equalsIgnoreCase(name)) {
                email.setSender(value);
            }
        }

        // Body
        email.setBody(extractBody(payload));

        // Thread ID
        email.setThreadId(json.get("threadId").asText());

        return email;
    }

    private String extractBody(JsonNode payload) {

        // Multipart emails
        if (payload.has("parts")) {
            for (JsonNode part : payload.get("parts")) {

                String mimeType = part.get("mimeType").asText();

                if ("text/plain".equals(mimeType)) {
                    String data = part.get("body").get("data").asText();
                    return decodeBase64(data);
                }
            }
        }

        // Simple emails
        if (payload.get("body") != null && payload.get("body").has("data")) {
            String data = payload.get("body").get("data").asText();
            return decodeBase64(data);
        }

        return "";
    }

    private String decodeBase64(String data) {
        return new String(Base64.getUrlDecoder().decode(data));
    }

    public List<String> getSentEmailBodies(User user) throws Exception {

        List<String> rawEmails;
        try {
            rawEmails = gmailClient.fetchEmailDetails(user.getAccessToken());
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401 && user.getRefreshToken() != null && !user.getRefreshToken().isBlank()) {
                String newAccessToken = googleOAuthTokenService.refreshAccessToken(user.getRefreshToken());
                user.setAccessToken(newAccessToken);
                userRepository.save(user);
                rawEmails = gmailClient.fetchEmailDetails(newAccessToken);
            } else {
                throw e;
            }
        }

        List<String> bodies = new ArrayList<>();

        for (String raw : rawEmails) {
            JsonNode json = mapper.readTree(raw);
            Email email = parseEmail(json);

            if (email.getBody() != null && !email.getBody().isEmpty()) {
                bodies.add(email.getBody());
            }
        }

        return bodies;
    }

    public String sendReply(User user, String messageId, String replyText) throws Exception {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId is required");
        }
        if (replyText == null || replyText.isBlank()) {
            throw new IllegalArgumentException("replyText is required");
        }

        try {
            String originalJson = gmailClient.getMessageDetails(messageId, user.getAccessToken());
            return sendReplyInternal(user, originalJson, replyText);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401 && user.getRefreshToken() != null && !user.getRefreshToken().isBlank()) {
                String newAccessToken = googleOAuthTokenService.refreshAccessToken(user.getRefreshToken());
                user.setAccessToken(newAccessToken);
                userRepository.save(user);
                String originalJson = gmailClient.getMessageDetails(messageId, newAccessToken);
                return sendReplyInternal(user, originalJson, replyText);
            }
            throw e;
        }
    }

    private String sendReplyInternal(User user, String originalJson, String replyText) throws Exception {
        JsonNode json = mapper.readTree(originalJson);
        String threadId = Optional.ofNullable(json.get("threadId")).map(JsonNode::asText).orElse(null);

        JsonNode payload = json.get("payload");
        String from = null;
        String subject = null;
        String messageIdHeader = null;
        String references = null;

        if (payload != null && payload.has("headers")) {
            for (JsonNode header : payload.get("headers")) {
                String name = header.get("name").asText("");
                String value = header.get("value").asText("");
                if ("From".equalsIgnoreCase(name)) from = value;
                if ("Subject".equalsIgnoreCase(name)) subject = value;
                if ("Message-ID".equalsIgnoreCase(name)) messageIdHeader = value;
                if ("References".equalsIgnoreCase(name)) references = value;
            }
        }

        if (from == null || from.isBlank()) {
            throw new RuntimeException("Could not read original From header");
        }
        if (messageIdHeader == null || messageIdHeader.isBlank()) {
            throw new RuntimeException("Could not read original Message-ID header");
        }

        String replySubject = (subject == null || subject.isBlank()) ? "Re:" : subject;
        if (!replySubject.toLowerCase().startsWith("re:")) {
            replySubject = "Re: " + replySubject;
        }

        StringBuilder rfc822 = new StringBuilder();
        rfc822.append("To: ").append(from).append("\r\n");
        rfc822.append("Subject: ").append(replySubject).append("\r\n");
        rfc822.append("In-Reply-To: ").append(messageIdHeader).append("\r\n");
        if (references != null && !references.isBlank()) {
            rfc822.append("References: ").append(references).append(" ").append(messageIdHeader).append("\r\n");
        } else {
            rfc822.append("References: ").append(messageIdHeader).append("\r\n");
        }
        rfc822.append("Content-Type: text/plain; charset=\"UTF-8\"\r\n");
        rfc822.append("\r\n");
        rfc822.append(replyText).append("\r\n");

        try {
            return gmailClient.sendMessage(user.getAccessToken(), threadId, rfc822.toString());
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401 && user.getRefreshToken() != null && !user.getRefreshToken().isBlank()) {
                String newAccessToken = googleOAuthTokenService.refreshAccessToken(user.getRefreshToken());
                user.setAccessToken(newAccessToken);
                userRepository.save(user);
                return gmailClient.sendMessage(newAccessToken, threadId, rfc822.toString());
            }
            throw e;
        }
    }


}