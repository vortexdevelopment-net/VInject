package net.vortexdevelopment.vinject.http.dispatcher;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.http.annotation.*;
import net.vortexdevelopment.vinject.di.context.InjectionContext;
import net.vortexdevelopment.vinject.http.annotation.*;

import jakarta.servlet.AsyncContext;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * The main servlet that handles all incoming HTTP requests and routes them
 * to the appropriate @RestController methods.
 */
public class VInjectDispatcherServlet extends HttpServlet {

    private final DependencyContainer container;
    private final List<RouteHandler> routes = new ArrayList<>();
    private final Gson gson = new Gson();
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();


    public VInjectDispatcherServlet(DependencyContainer container) {
        this.container = container;
        initializeRoutes();
    }

    private void initializeRoutes() {
        // Find all beans annotated with @RestController
        for (Map.Entry<Class<?>, Object> entry : container.getDependencies().entrySet()) {
            Class<?> clazz = entry.getKey();
            Object instance = entry.getValue();

            if (clazz.isAnnotationPresent(RestController.class)) {
                System.out.println("Found @RestController: " + clazz.getName());
                RestController restController = clazz.getAnnotation(RestController.class);
                String basePath = restController.value();

                // Scan methods for routing annotations
                for (Method method : clazz.getDeclaredMethods()) {
                    String path = null;
                    String httpMethod = null;

                    if (method.isAnnotationPresent(RequestMapping.class)) {
                        RequestMapping req = method.getAnnotation(RequestMapping.class);
                        path = req.value();
                        httpMethod = req.method().toUpperCase();
                    } else if (method.isAnnotationPresent(GetMapping.class)) {
                        path = method.getAnnotation(GetMapping.class).value();
                        httpMethod = "GET";
                    } else if (method.isAnnotationPresent(PostMapping.class)) {
                        path = method.getAnnotation(PostMapping.class).value();
                        httpMethod = "POST";
                    } else if (method.isAnnotationPresent(PutMapping.class)) {
                        path = method.getAnnotation(PutMapping.class).value();
                        httpMethod = "PUT";
                    } else if (method.isAnnotationPresent(DeleteMapping.class)) {
                        path = method.getAnnotation(DeleteMapping.class).value();
                        httpMethod = "DELETE";
                    }

                    if (path != null && httpMethod != null) {
                        String fullPath = normalizePath(basePath + path);
                        System.out.println("Registered Route: " + httpMethod + " " + fullPath + " -> " + method.getName());
                        routes.add(new RouteHandler(fullPath, httpMethod, instance, method));
                    }
                }
            }
        }
    }

    private String normalizePath(String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path.replaceAll("/+", "/");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AsyncContext asyncContext = req.startAsync(req, resp);
        virtualThreadExecutor.submit(() -> {
            boolean dispatched = false;
            try {
                dispatched = handleRequest(
                        (HttpServletRequest) asyncContext.getRequest(),
                        (HttpServletResponse) asyncContext.getResponse(),
                        asyncContext
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (!dispatched) {
                    asyncContext.complete();
                }
            }
        });
    }

    private boolean handleRequest(HttpServletRequest req, HttpServletResponse resp, AsyncContext asyncContext) throws IOException {
        String path = req.getRequestURI();
        String method = req.getMethod();
        System.out.println("Incoming Request: " + method + " " + path);

        RouteHandler handler = null;
        for (RouteHandler route : routes) {
            if (route.matches(path, method)) {
                handler = route;
                break;
            }
        }

        if (handler != null) {
            try {
                Map<Class<?>, Object> requestContext = new HashMap<>();
                requestContext.put(HttpServletRequest.class, req);
                requestContext.put(HttpServletResponse.class, resp);

                RouteHandler finalHandler = handler;
                Object result = InjectionContext.runWithContext(requestContext, () -> finalHandler.invoke(container));

                if (result != null) {
                    if (result instanceof String resultStr && resultStr.startsWith("forward:")) {
                        String forwardPath = resultStr.substring("forward:".length());
                        asyncContext.dispatch(forwardPath);
                        return true;
                    }
                    resp.setContentType("application/json");
                    resp.getWriter().write(gson.toJson(result));
                }
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("Internal Server Error: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Try to serve static content from resources/static
            String resourcePath = "/static" + (path.equals("/") ? "/index.html" : path);
            URL resource = getClass().getResource(resourcePath);

            if (resource != null) {
                String contentType = getServletContext().getMimeType(path);
                if (contentType == null) {
                    if (path.endsWith(".html")) contentType = "text/html";
                    else if (path.endsWith(".css")) contentType = "text/css";
                    else if (path.endsWith(".js")) contentType = "application/javascript";
                    else if (path.endsWith(".png")) contentType = "image/png";
                    else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) contentType = "image/jpeg";
                }

                if (contentType != null) {
                    resp.setContentType(contentType);
                }

                try (java.io.InputStream is = resource.openStream()) {
                    is.transferTo(resp.getOutputStream());
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("Not Found");
            }
        }
        return false;
    }

    private static class RouteHandler {
        private final String path;
        private final String httpMethod;
        private final Object instance;
        private final Method method;

        public RouteHandler(String path, String httpMethod, Object instance, Method method) {
            this.path = path;
            this.httpMethod = httpMethod;
            this.instance = instance;
            this.method = method;
            this.method.setAccessible(true);
        }

        public boolean matches(String requestPath, String requestMethod) {
            // Very basic matching, could be enhanced for path variables
            return this.path.equals(requestPath) && this.httpMethod.equals(requestMethod);
        }

        public Object invoke(DependencyContainer container) throws InvocationTargetException, IllegalAccessException {
            Object[] args = container.resolveMethodArgumentValues(instance.getClass(), method, instance);
            return method.invoke(instance, args);
        }
    }
}
