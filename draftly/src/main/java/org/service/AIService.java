package org.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AIService {

    private final ChatClient chatClient;

    public AIService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String generateReply(String incomingEmail, String toneContext) {
        return generateReply(incomingEmail, toneContext, "DEFAULT");
    }

    public String generateReply(String incomingEmail, String toneContext, String tone) {

        String toneRule = switch ((tone == null ? "DEFAULT" : tone.toUpperCase())) {
            case "FORMAL" -> "Write in a formal, professional tone.";
            case "FRIENDLY" -> "Write in a warm, friendly, approachable tone.";
            case "CONCISE" -> "Write a concise reply (keep it short while still complete).";
            default -> "Write in a professional tone matching the examples.";
        };

        String prompt = """
        You are an AI assistant that writes professional email replies.

        IMPORTANT OUTPUT RULES:
        - Do NOT include placeholders like [Your Name], [Your Email Address], [Your Contact Information].
        - Do NOT invent or add a signature block. End the reply at the closing line (e.g., Thanks/Regards) only if it is clearly implied by the tone examples.
        - Output only the reply body as plain text. No subject line, no markdown, no quoted original email.

        Tone requirement:
        %s

        Learn the tone and style from these examples:
        %s

        Now write a reply to this email:
        %s
        """.formatted(toneRule, toneContext, incomingEmail);

        return callOpenAI(prompt);
    }

    public String callOpenAI(String prompt) {
        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            // Surface the real OpenAI/proxy error message instead of only the RestClient parsing issue.
            throw new RuntimeException("OpenAI chat call failed: " + e.getMessage(), e);
        }
    }

}