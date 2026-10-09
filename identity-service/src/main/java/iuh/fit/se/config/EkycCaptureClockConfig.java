package iuh.fit.se.config;
import java.time.Clock;
import org.springframework.context.annotation.*;
@Configuration
public class EkycCaptureClockConfig {
    @Bean public Clock ekycCaptureClock() { return Clock.systemUTC(); }
}
