package net.vortexdevelopment.vinject.http.app;

import net.vortexdevelopment.vinject.VInjectApplication;
import net.vortexdevelopment.vinject.annotation.component.Root;

@Root
public class Main {

    public static void main(String[] args) {
        VInjectApplication.run(Main.class, args);
    }
}
