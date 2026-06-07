package net.vortexdevelopment.plugin.vinject.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import net.vortexdevelopment.plugin.vinject.Plugin;
import org.jetbrains.annotations.NotNull;

public class ReloadVInjectAction extends AnAction {

    public ReloadVInjectAction() {
        super("Reload VInject");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        if (project == null) return;
        // Call plugin rescan method
        Plugin.rescanProject(project);
    }
}


