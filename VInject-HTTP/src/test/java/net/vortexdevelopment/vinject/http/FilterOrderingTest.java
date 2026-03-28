package net.vortexdevelopment.vinject.http;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import net.vortexdevelopment.vinject.VInjectApplication;
import net.vortexdevelopment.vinject.annotation.Order;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.http.annotation.GetMapping;
import net.vortexdevelopment.vinject.http.annotation.RestController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Root
public class FilterOrderingTest {

    private static DependencyContainer container;
    private static final List<String> filterOrder = new ArrayList<>();

    @BeforeAll
    public static void setup() throws Exception {
        System.setProperty("server.port", "8089");
        new Thread(() -> {
            try {
                container = VInjectApplication.run(FilterOrderingTest.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        // Wait for server
        Thread.sleep(2000);
    }

    @AfterAll
    public static void teardown() {
        VInjectApplication.shutdown(false);
    }

    @Test
    public void testFilterOrdering() throws Exception {
        filterOrder.clear();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8089/test/filter"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        // Verify order: Filter1 (Order 1) should be before Filter2 (Order 2)
        assertEquals(List.of("Filter1", "Filter2"), filterOrder);
    }

    @Component
    @Order(1)
    public static class Filter1 implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            System.out.println("Filter1 executing");
            filterOrder.add("Filter1");
            chain.doFilter(request, response);
        }
    }

    @Component
    @Order(2)
    public static class Filter2 implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            System.out.println("Filter2 executing");
            filterOrder.add("Filter2");
            chain.doFilter(request, response);
        }
    }

    @RestController
    public static class FilterTestController {
        @GetMapping("/test/filter")
        public String test() {
            return "ok";
        }
    }
}
