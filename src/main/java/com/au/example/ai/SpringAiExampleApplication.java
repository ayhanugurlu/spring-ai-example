package com.au.example.ai;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class SpringAiExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiExampleApplication.class, args);
    }


    @Bean
    ApplicationRunner example(VectorStore vectorStore, JdbcTemplate jdbcTemplate, Chatbot chatbot) {
        Resource pdfResource = new ClassPathResource("");
        return args -> {

            setup(vectorStore, jdbcTemplate, pdfResource);
            var response = chatbot.chat("What is the purpose of the document?");
            System.out.println(Map.of("response", response));
        };
    }

    private static void setup(VectorStore vectorStore, JdbcTemplate jdbcTemplate, Resource pdfResource) {
        jdbcTemplate.update("delete from vectore_store");

        var config = PdfDocumentReaderConfig.builder().withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder().withNumberOfBottomTextLinesToDelete(3).build()).build();
        var pdfReader = new PagePdfDocumentReader(pdfResource, config);
        var testSplitter = new TokenTextSplitter();
        var docs = testSplitter.apply(pdfReader.get());
        vectorStore.accept(docs);
    }


}


@Component
class Chatbot {


    private final String template = """
                        
            """;
    private final ChatClient aiClient;
    private final VectorStore vectorStore;

    Chatbot(ChatClient aiClient, VectorStore vectorStore) {
        this.aiClient = aiClient;
        this.vectorStore = vectorStore;
    }

    public String chat(String message) {
        var listOfSimilarDocuments = this.vectorStore.similaritySearch(message);
        var documents = listOfSimilarDocuments
                .stream()
                .map(Document::getContent)
                .collect(Collectors.joining(System.lineSeparator()));
        var systemMessage = new SystemPromptTemplate(this.template)
                .createMessage(Map.of("documents", documents));
        var userMessage = new UserMessage(message);
        var prompt = new Prompt(List.of(systemMessage, userMessage));
        var aiResponse = aiClient.call(prompt);
        return aiResponse.getResult().getOutput().getContent();
    }
}
