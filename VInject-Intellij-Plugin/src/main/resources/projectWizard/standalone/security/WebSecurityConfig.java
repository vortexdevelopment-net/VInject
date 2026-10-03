package $PACKAGE$.security;

import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Provides;
import net.vortexdevelopment.vinject.http.filter.CorsFilter;
import net.vortexdevelopment.vinject.http.filter.CsrfFilter;

@Component
public class WebSecurityConfig {

    @Provides
    public CorsFilter corsFilter() {
        return CorsFilter.builder()
                .allowOrigin("*")
                .allowMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowHeaders("Content-Type", "Authorization")
                .build();
    }

    @Provides
    public CsrfFilter csrfFilter() {
        // Basic CSRF protection example
        return new CsrfFilter();
    }
}
