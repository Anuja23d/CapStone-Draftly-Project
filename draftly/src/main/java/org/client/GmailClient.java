package org.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class GmailClient {

    private final WebClient webClient;

    public GmailClient() {
        // Increase buffer size limit to 5MB (5242880 bytes) to handle large email responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                // Use the generic Google APIs host; some networks/DNS block gmail.googleapis.com.
                .baseUrl("https://www.googleapis.com")
                .exchangeStrategies(strategies)
                .build();
    }

    // 🔹 Step 1: Get message IDs
    public String getMessages(String accessToken) {
        return webClient.get()
                .uri("/gmail/v1/users/me/messages?q=in:sent&maxResults=5")
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String listMessages(String accessToken, String query, int maxResults) {
        String q = (query == null) ? "" : query;
        int max = Math.max(1, Math.min(maxResults, 50));
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/gmail/v1/users/me/messages")
                        .queryParam("q", q)
                        .queryParam("maxResults", max)
                        .build())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    // 🔹 Step 2: Get full message details
    public String getMessageDetails(String messageId, String accessToken) {
        return webClient.get()
                .uri("/gmail/v1/users/me/messages/" + messageId + "?format=full")
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String sendMessage(String accessToken, String threadId, String rawRfc822) {
        String rawBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(rawRfc822.getBytes());

        String jsonBody = (threadId == null || threadId.isBlank())
                ? "{\"raw\":\"" + rawBase64Url + "\"}"
                : "{\"threadId\":\"" + threadId + "\",\"raw\":\"" + rawBase64Url + "\"}";

        return webClient.post()
                .uri("/gmail/v1/users/me/messages/send")
                .headers(h -> h.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    // 🔹 Step 3: Fetch all email details
    public List<String> fetchEmailDetails(String accessToken) throws Exception {

        String response = getMessages(accessToken);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);

        List<String> emails = new ArrayList<>();

        for (JsonNode msg : root.get("messages")) {
            String id = msg.get("id").asText();
            emails.add(getMessageDetails(id, accessToken));
        }

        return emails;
    }

}
