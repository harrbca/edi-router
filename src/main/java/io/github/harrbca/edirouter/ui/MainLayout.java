package io.github.harrbca.edirouter.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.github.harrbca.edirouter.ui.view.DashboardView;

public class MainLayout extends AppLayout {

    public MainLayout() {
        setPrimarySection(Section.DRAWER);
        createHeader();
        createDrawer();
        setDrawerOpened(true);
    }

    private void createHeader() {
        H1 logo = new H1("M3 Data Sync");
        logo.addClassNames(
                LumoUtility.FontSize.LARGE,
                LumoUtility.Margin.MEDIUM
        );

        Span status = new Span("Service Running");
        status.getElement().getThemeList().add("badge success");

        HorizontalLayout header = new HorizontalLayout(
                new DrawerToggle(),
                logo
        );
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.expand(logo);
        header.setWidthFull();
        header.addClassNames(
                LumoUtility.Padding.Vertical.NONE,
                LumoUtility.Padding.Horizontal.MEDIUM
        );

        addToNavbar(header);
    }

    private void createDrawer() {
        SideNav nav = new SideNav();

        nav.addItem(new SideNavItem("Dashboard", DashboardView.class, VaadinIcon.DASHBOARD.create()));

        addToDrawer(nav);
    }


}
