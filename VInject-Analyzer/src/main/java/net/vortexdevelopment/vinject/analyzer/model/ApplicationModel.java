package net.vortexdevelopment.vinject.analyzer.model;

import net.vortexdevelopment.vinject.di.registry.RegistryOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record ApplicationModel(Set<BeanModel> beans,
                               List<RegistryHandlerModel> registryHandlers,
                               List<RegistryTargetModel> registryTargets) {

    public ApplicationModel(Set<BeanModel> beans, List<RegistryHandlerModel> registryHandlers, List<RegistryTargetModel> registryTargets) {
        this.beans = Collections.unmodifiableSet(new LinkedHashSet<>(beans));
        this.registryHandlers = Collections.unmodifiableList(new ArrayList<>(registryHandlers));
        this.registryTargets = Collections.unmodifiableList(new ArrayList<>(registryTargets));
    }

    public List<RegistryTargetModel> getRegistryTargets(RegistryOrder order) {
        return registryTargets.stream()
                .filter(target -> target.order() == order)
                .collect(Collectors.toList());
    }

    public EnumMap<RegistryOrder, List<RegistryTargetModel>> getRegistryTargetsByOrder() {
        EnumMap<RegistryOrder, List<RegistryTargetModel>> byOrder = new EnumMap<>(RegistryOrder.class);
        for (RegistryTargetModel target : registryTargets) {
            byOrder.computeIfAbsent(target.order(), ignored -> new ArrayList<>()).add(target);
        }
        return byOrder;
    }
}
