package iuh.fit.se.contractservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TripRouteLocation {
    private Double latitude;
    private Double longitude;
    @Column(length = 120)
    private String label;
    @Column(length = 1000)
    private String address;
    @Column(length = 30)
    private String source;
}
