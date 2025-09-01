package io.github.harrbca.edirouter.ui.view;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.component.page.Push;
import io.github.harrbca.edirouter.event.FileProcessedEvent;
import io.github.harrbca.edirouter.model.LogEntry;
import io.github.harrbca.edirouter.service.FileMonitorService;
import io.github.harrbca.edirouter.service.LogEventService;
import io.github.harrbca.edirouter.ui.MainLayout;
import io.github.harrbca.edirouter.ui.util.Broadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

@Slf4j
@Route(value = "", layout = MainLayout.class)
@PageTitle("M3 Data Sync - Dashboard")
@RouteScope
@SpringComponent
@Push
public class DashboardView extends VerticalLayout {

    private final LogEventService logEventService;
    private final FileMonitorService fileMonitorService;
    private final Grid<LogEntry> activityGrid = new Grid<>(LogEntry.class, false);
    private final Span errorCountBadge = new Span();
    private final Span warningCountBadge = new Span();
    private final Span totalCountBadge = new Span();
    private final Span monitorStatusBadge = new Span();
    
    private Consumer<LogEntry> logListener;
    private volatile long filesProcessedCount = 0;

    public DashboardView(LogEventService logEventService, FileMonitorService fileMonitorService) {
        this.logEventService = logEventService;
        this.fileMonitorService = fileMonitorService;
        
        setSizeFull();
        setSpacing(true);
        setPadding(true);
        
        createHeader();
        createStatusCards();
        createRecentActivitySection();
        refreshData();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        
        // Register this dashboard for broadcasting
        Broadcaster.register(this, getUI().orElseThrow());
        
        // Set up real-time log updates
        logListener = this::handleNewLogEntry;
        logEventService.addLogListener(logListener);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        
        // Unregister this dashboard from broadcasting
        Broadcaster.unregister(this);
        
        // Clean up listener
        if (logListener != null) {
            logEventService.removeLogListener(logListener);
        }
    }

    private void createHeader() {
        H1 title = new H1("M3 Data Sync Dashboard");
        title.getStyle().set("margin-bottom", "0");
        
        Button refreshButton = new Button("Refresh", VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        refreshButton.addClickListener(e -> refreshData());

        
        HorizontalLayout header = new HorizontalLayout(title, new HorizontalLayout(refreshButton));
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.setWidthFull();
        
        add(header);
    }

    private void createStatusCards() {
        // Monitor Status Card
        Div monitorCard = createStatusCard("File Monitor", monitorStatusBadge, VaadinIcon.EYE);
        
        // Error Count Card
        Div errorCard = createStatusCard("Errors", errorCountBadge, VaadinIcon.EXCLAMATION_CIRCLE);
        errorCountBadge.getStyle().set("color", "var(--lumo-error-text-color)");
        
        // Warning Count Card
        Div warningCard = createStatusCard("Warnings", warningCountBadge, VaadinIcon.WARNING);
        warningCountBadge.getStyle().set("color", "var(--lumo-warning-text-color)");
        
        // Files Processed Card
        Div totalCard = createStatusCard("Files Processed", totalCountBadge, VaadinIcon.RECORDS);
        totalCountBadge.getStyle().set("color", "var(--lumo-success-text-color)");
        
        HorizontalLayout statusLayout = new HorizontalLayout(monitorCard, errorCard, warningCard, totalCard);
        statusLayout.setWidthFull();
        statusLayout.setSpacing(true);
        
        add(statusLayout);
    }

    private Div createStatusCard(String title, Span valueBadge, VaadinIcon icon) {
        Div card = new Div();
        card.addClassName("status-card");
        card.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("padding", "var(--lumo-space-m)")
            .set("flex", "1")
            .set("text-align", "center");

        Span iconSpan = new Span(icon.create());
        iconSpan.getStyle().set("font-size", "2em").set("color", "var(--lumo-primary-color)");
        
        Span titleSpan = new Span(title);
        titleSpan.getStyle().set("display", "block").set("font-weight", "bold").set("margin", "0.5em 0");
        
        valueBadge.getStyle()
            .set("font-size", "1.5em")
            .set("font-weight", "bold")
            .set("display", "block");

        card.add(iconSpan, titleSpan, valueBadge);
        return card;
    }

    private void createRecentActivitySection() {
        H3 sectionTitle = new H3("Recent Activity");
        sectionTitle.getStyle().set("margin-bottom", "var(--lumo-space-s)");
        
        setupActivityGrid();
        
        add(sectionTitle, activityGrid);
    }

    private void setupActivityGrid() {
        activityGrid.setHeightFull();
        activityGrid.addClassName("activity-grid");
        
        // Timestamp column
        activityGrid.addColumn(entry -> 
            entry.getTimestamp().format(DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")))
            .setHeader("Time")
            .setFlexGrow(0).setAutoWidth(true);
        
        // Level column with icon
        activityGrid.addColumn(new ComponentRenderer<>(entry -> {
            Span levelSpan = new Span(entry.getLevelIcon() + " " + entry.getLevel());
            levelSpan.getStyle().set("font-weight", "bold");
            
            // Color coding
            switch (entry.getLevel()) {
                case ERROR -> levelSpan.getStyle().set("color", "var(--lumo-error-text-color)");
                case WARN -> levelSpan.getStyle().set("color", "var(--lumo-warning-text-color)");
                case INFO -> levelSpan.getStyle().set("color", "var(--lumo-success-text-color)");
                default -> levelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
            }
            
            return levelSpan;
        }))
        .setHeader("Level")
        .setAutoWidth(true)
        .setFlexGrow(0);
        
        // Category column with icon
        activityGrid.addColumn(new ComponentRenderer<>(entry -> {
            Span categorySpan = new Span(entry.getCategoryIcon() + " " + entry.getCategory());
            return categorySpan;
        }))
        .setHeader("Category")
        .setAutoWidth(true)
        .setFlexGrow(0);
        
        // Message column
        activityGrid.addColumn(LogEntry::getFormattedMessage)
            .setHeader("Message")
            .setFlexGrow(1);
            
        // Style the grid
        activityGrid.getStyle()
            .set("font-size", "var(--lumo-font-size-s)")
            .set("--lumo-font-family", "var(--lumo-font-family-monospace)");
    }

    private void refreshData() {
        // Update status badges
        updateStatusBadges();
        
        // Refresh activity grid
        activityGrid.setItems(logEventService.getRecentLogEntries(100));
        
        log.debug("Dashboard data refreshed");
    }

    private void updateStatusBadges() {
        // Monitor status
        if (fileMonitorService.isRunning()) {
            monitorStatusBadge.setText("✅ Running");
            monitorStatusBadge.getStyle().set("color", "var(--lumo-success-text-color)");
        } else {
            monitorStatusBadge.setText("❌ Stopped");
            monitorStatusBadge.getStyle().set("color", "var(--lumo-error-text-color)");
        }
        
        // Error count
        long errorCount = logEventService.getErrorCount();
        errorCountBadge.setText(String.valueOf(errorCount));
        
        // Warning count
        long warningCount = logEventService.getWarningCount();
        warningCountBadge.setText(String.valueOf(warningCount));
        
        // Files processed count
        totalCountBadge.setText(String.valueOf(filesProcessedCount));
    }

    private void handleNewLogEntry(LogEntry entry) {
        // Update UI in the browser thread
        getUI().ifPresent(ui -> ui.access(() -> {
            // Update status badges
            updateStatusBadges();

            // Refresh the grid with fresh data
            activityGrid.setItems(logEventService.getRecentLogEntries(100));

            // Scroll to top to show newest entry
            activityGrid.scrollToStart();
        }));
    }
    
    @EventListener
    public void handleFileProcessedEvent(FileProcessedEvent event) {
        // Broadcast file processed event to all active dashboard sessions
        Broadcaster.broadcastFileProcessedEvent(event);
    }
    
    public void updateFileProcessedCount(long count) {
        filesProcessedCount = count;
        totalCountBadge.setText(String.valueOf(count));
    }
}