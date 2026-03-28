package net.vortexdevelopment.vinject.http.registry;

import jakarta.servlet.Filter;
import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.Order;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.di.ComponentInterceptor;
import net.vortexdevelopment.vinject.di.DependencyContainer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Automatically detects and registers Jakarta Servlet Filters.
 * Filters are ordered based on the @Order annotation.
 */
@Component
public class FilterRegistry implements ComponentInterceptor {

    private final List<FilterEntry> filters = new ArrayList<>();

    public List<FilterEntry> getFilters() {
        return filters;
    }

    @Override
    public void onComponentRegistered(Class<?> clazz, Object instance, DependencyContainer container) {
        if (instance instanceof Filter filter) {
            int order = 0;
            if (clazz.isAnnotationPresent(Order.class)) {
                order = clazz.getAnnotation(Order.class).value();
            }
            filters.add(new FilterEntry(filter, order, clazz.getSimpleName()));
            
            // Re-sort filters based on order
            filters.sort(Comparator.comparingInt(FilterEntry::order));
        }
    }

}
