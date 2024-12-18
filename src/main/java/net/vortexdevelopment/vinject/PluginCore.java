package net.vortexdevelopment.vinject;

import net.vortexdevelopment.vinject.annotation.PluginRoot;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class PluginCore extends JavaPlugin {

    private DependencyContainer dependencyContainer;

    @Override
    public final void onLoad() {
        onPluginLoad();
    }

    @Override
    public final void onEnable() {
        //Check if the current declaring class has a PluginRoot annotation, if so get the base package name
        if (getClass().isAnnotationPresent(PluginRoot.class)) {
            String packageName = getClass().getAnnotation(PluginRoot.class).packageName();
            //Scan the packages for

            dependencyContainer = new DependencyContainer(packageName, getClass(), this);
            dependencyContainer.inject(this); //inject root class after all components are loaded


            onPluginEnable();
        } else {
            getLogger().severe("PluginRoot annotation not found in main class: " + getClass().getName());
            getLogger().severe("PluginRoot annotation is required to start the plugin");
            getLogger().severe("Plugin will not start");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public final void onDisable() {
        // Plugin shutdown logic
        onPluginDisable();
    }

    public abstract void onPluginLoad();

    public abstract void onPluginEnable();

    public abstract void onPluginDisable();

    //Use Unsafe for now, will switch to JOL later if Unsafe is broken
//    public void prepareJol() {
//        //Run a test query to initialize JOL
//        ClassLayout.parseClass(Object.class);
//    }
//
//    public long getOffset(Class<?> clazz, Field field) {
//        if (classLayoutMap.containsKey(clazz.getName())) {
//            FieldLayout fieldLayout = classLayoutMap.get(clazz.getName()).fields().stream().filter(f -> f.name().equals(field.getName())).findFirst().orElse(null);
//            if (fieldLayout == null) {
//                return -1;
//            }
//            return fieldLayout.offset();
//        }
//
//        long nanoseconds = System.nanoTime();
//        ClassLayout classLayout = ClassLayout.parseClass(clazz);
//        //Store the class layout for later use
//        classLayoutMap.put(clazz.getName(), classLayout);
//
//        System.err.println("Parsing class took: " + (System.nanoTime() - nanoseconds) + "ns");
//        FieldLayout fieldLayout = classLayout.fields().stream().filter(f -> f.name().equals(field.getName())).findFirst().orElse(null);
//        if (fieldLayout == null) {
//            return -1;
//        }
//        return fieldLayout.offset();
//    }
//
//    public static Unsafe getUnsafe() {
//        try {
//            Field field = Unsafe.class.getDeclaredField("theUnsafe");
//            field.setAccessible(true); // Make the field accessible
//            return (Unsafe) field.get(null); // Get the Unsafe instance
//        } catch (Exception e) {
//            throw new RuntimeException("Unable to access Unsafe", e);
//        }
//    }
}
