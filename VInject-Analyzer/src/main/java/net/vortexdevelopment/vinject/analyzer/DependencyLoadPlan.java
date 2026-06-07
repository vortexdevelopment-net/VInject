package net.vortexdevelopment.vinject.analyzer;

import java.util.List;

public record DependencyLoadPlan(List<Class<?>> serviceLoadOrder, List<Class<?>> componentLoadOrder) {

    public DependencyLoadPlan(List<Class<?>> serviceLoadOrder, List<Class<?>> componentLoadOrder) {
        this.serviceLoadOrder = List.copyOf(serviceLoadOrder);
        this.componentLoadOrder = List.copyOf(componentLoadOrder);
    }

}
