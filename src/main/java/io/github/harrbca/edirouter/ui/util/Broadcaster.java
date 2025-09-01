package io.github.harrbca.edirouter.ui.util;

import com.vaadin.flow.component.UI;
import io.github.harrbca.edirouter.event.FileProcessedEvent;
import io.github.harrbca.edirouter.ui.view.DashboardView;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

@Slf4j
public class Broadcaster {
    
    private static final ConcurrentMap<DashboardView, UI> dashboardRegistry = new ConcurrentHashMap<>();
    
    public static void register(DashboardView dashboard, UI ui) {
        dashboardRegistry.put(dashboard, ui);
        ui.addDetachListener(event -> {
            unregister(dashboard);
        });
        log.debug("Dashboard registered for broadcasting: {}", ui.getUIId());
    }
    
    public static void unregister(DashboardView dashboard) {
        dashboardRegistry.remove(dashboard);
        log.debug("Dashboard unregistered from broadcasting");
    }
    
    public static void broadcastFileProcessedEvent(FileProcessedEvent event) {
        dashboardRegistry.forEach((dashboard, ui) -> {
            if (ui.isAttached()) {
                ui.access(() -> {
                    try {
                        dashboard.updateFileProcessedCount(event.getTotalFilesProcessed());
                    } catch (Exception e) {
                        log.warn("Error broadcasting to dashboard {}: {}", ui.getUIId(), e.getMessage());
                    }
                });
            } else {
                // Clean up detached UIs
                unregister(dashboard);
            }
        });
        
        log.debug("Broadcasted file processed event to {} active dashboard sessions", dashboardRegistry.size());
    }
    
    public static int getActiveDashboardCount() {
        return dashboardRegistry.size();
    }
}