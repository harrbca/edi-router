package io.github.harrbca.edirouter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.cli")
public class CliProperties {
    
    private boolean enabled = true;
    private String prompt = "edi-router> ";
    private boolean showWelcomeMessage = true;
}