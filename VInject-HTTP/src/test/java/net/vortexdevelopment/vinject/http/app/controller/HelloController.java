package net.vortexdevelopment.vinject.http.app.controller;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import net.vortexdevelopment.vinject.http.annotation.GetMapping;
import net.vortexdevelopment.vinject.http.annotation.RestController;

import java.io.IOException;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String sayHello(HttpServletRequest request) {
        //Debug print request address
        System.out.println("Received request from: " + request.getRemoteAddr());
        return "Hello, World!";
    }

    @GetMapping("/")
    public String root() {
        return "forward:/index.html";
    }

    private static class TestFilter implements Filter {

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        }
    }
}
