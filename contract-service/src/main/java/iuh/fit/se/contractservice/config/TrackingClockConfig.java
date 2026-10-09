package iuh.fit.se.contractservice.config;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TrackingClockConfig {
    @Bean public Clock trackingClock() { return Clock.systemUTC(); }
}
