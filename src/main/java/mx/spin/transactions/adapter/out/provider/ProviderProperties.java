package mx.spin.transactions.adapter.out.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.provider")
public record ProviderProperties(String baseUrl, String executePath,
                                 Duration connectTimeout, Duration readTimeout) { }