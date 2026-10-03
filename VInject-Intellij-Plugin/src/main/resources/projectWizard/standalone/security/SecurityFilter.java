package $PACKAGE$.security;

import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.http.filter.HttpFilter;
import net.vortexdevelopment.vinject.http.request.HttpRequest;
import net.vortexdevelopment.vinject.http.response.HttpResponse;
import net.vortexdevelopment.vinject.http.filter.FilterChain;

@Component
public class SecurityFilter implements HttpFilter {

    @Override
    public void filter(HttpRequest request, HttpResponse response, FilterChain chain) {
        String authHeader = request.getHeader("Authorization");
        
        // Example: Extract user from token
        String user = "Anonymous";
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            user = "User_" + authHeader.substring(7);
        }

        // Bind user to the request scope using Scoped Value
        ScopedValue.where(UserContext.USER, user).run(() -> {
            chain.doFilter(request, response);
        });
    }
}
