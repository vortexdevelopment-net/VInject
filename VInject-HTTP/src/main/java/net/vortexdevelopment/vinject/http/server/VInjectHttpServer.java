package net.vortexdevelopment.vinject.http.server;

import jakarta.servlet.DispatcherType;
import net.vortexdevelopment.vinject.config.Environment;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.http.dispatcher.VInjectDispatcherServlet;
import net.vortexdevelopment.vinject.http.registry.FilterEntry;
import net.vortexdevelopment.vinject.http.registry.FilterRegistry;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.util.EnumSet;

/**
 * Manages the embedded Jetty HTTP server.
 */
public class VInjectHttpServer {

    private static Server server;

    /**
     * Starts the embedded Jetty server on the port specified by the environment property "server.port".
     * Routes all requests to the VInjectDispatcherServlet.
     *
     * @param container The dependency container
     */
    public static void start(DependencyContainer container) {
        if (server != null && server.isStarted()) {
            return;
        }

        Environment env = Environment.getInstance();
        int port = 8080; // default
        if (env != null) {
            port = env.getPropertyAsInt("server.port", 8080);
        }

        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        VInjectDispatcherServlet dispatcherServlet = new VInjectDispatcherServlet(container);
        ServletHolder servletHolder = new ServletHolder(dispatcherServlet);
        servletHolder.setAsyncSupported(true);
        context.addServlet(servletHolder, "/*");

        // Register Filters from FilterRegistry
        try {
            FilterRegistry filterRegistry = container.getDependencyOrNull(FilterRegistry.class);
            if (filterRegistry != null) {
                for (FilterEntry entry : filterRegistry.getFilters()) {
                    FilterHolder filterHolder = new FilterHolder(entry.filter());
                    filterHolder.setAsyncSupported(true);
                    context.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
                    System.out.println("Registered Filter: " + entry.name() + " with order " + entry.order());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to register filters: " + e.getMessage());
        }

        try {
            server.start();
            System.out.println("VInject HTTP Server started on port " + port);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start embedded Jetty HTTP server", e);
        }
    }

    /**
     * Stops the embedded HTTP server.
     */
    public static void stop() {
        if (server != null && server.isStarted()) {
            try {
                server.stop();
                System.out.println("VInject HTTP Server stopped");
            } catch (Exception e) {
                System.err.println("Error stopping HTTP server: " + e.getMessage());
            }
        }
    }
}
