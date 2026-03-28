package net.vortexdevelopment.vinject.http;

import net.vortexdevelopment.vinject.VInjectApplication;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.lifecycle.PostConstruct;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.http.annotation.GetMapping;
import net.vortexdevelopment.vinject.http.annotation.PostMapping;
import net.vortexdevelopment.vinject.http.annotation.RestController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Root(ignoredPackages = "net.vortexdevelopment.vinject.http.app")
public class VInjectHttpServerTest {

    private static DependencyContainer container;

    @BeforeAll
    public static void setup() {
        System.setProperty("server.port", "8081");
        System.out.println("setup() starting");
        // Run the example app via VInjectApplication to trigger the auto-startup hook
        new Thread(() -> {
            try {
                System.out.println("Thread running VInjectApplication...");
                container = VInjectApplication.run(VInjectHttpServerTest.class);
                System.out.println("VInjectApplication returned container: " + container);
            } catch (Throwable e) {
                System.err.println("Error during VInjectApplication.run:");
                e.printStackTrace();
            }
        }).start();

        // Wait a bit for server to start
        try {
            System.out.println("Waiting 1000ms for server to start...");
            Thread.sleep(1000);
            System.out.println("Finished waiting.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @AfterAll
    public static void teardown() {
        VInjectApplication.shutdown(false);
    }

    @Test
    public void testGetEndpoint() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/api/hello"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        System.err.println("Response body: " + response.body());
        assertTrue(response.body().contains("Hello, World!"));
        assertTrue(response.body().contains("success"));
        System.out.println("GET Response: " + response.body());
    }

    @Test
    public void testStaticContent() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("VInject Web App"));
        assertTrue(response.body().contains("Static file served successfully!"));
        System.out.println("Static Response status: " + response.statusCode());
    }

    @Test
    public void testPostEndpoint() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/api/data"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Data received"));
        System.out.println("POST Response: " + response.body());
    }

    @Test
    public void testNotFoundEndpoint() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/api/nonexistent"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    @RestController
    private static class TestController {

        @PostConstruct
        public void init() {
            System.out.println("TestController initialized");
        }

        @GetMapping("/api/hello")
        public String hello() {
            return "{\"message\": \"Hello, World!\", \"status\": \"success\"}";
        }

        @PostMapping("/api/data")
        public String postData() {
            return "{\"message\": \"Data received\"}";
        }
    }

    public static void main(String[] args) throws Exception {
        setup();
        try {
            VInjectHttpServerTest test = new VInjectHttpServerTest();
            test.testGetEndpoint();
            test.testStaticContent();
            test.testPostEndpoint();
            test.testNotFoundEndpoint();
            System.out.println("Tests passed!");
        } finally {
            teardown();
        }
    }
}
