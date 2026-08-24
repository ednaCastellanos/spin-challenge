package mx.spin.transactions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.jwt")
public record SecurityProperties(String secret, Duration expiration) { }