package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.*;
import iuh.fit.se.contractservice.domain.enums.*;
import iuh.fit.se.contractservice.dto.*;
import iuh.fit.se.contractservice.repository.*;
import iuh.fit.se.contractservice.service.driveraccess.DriverAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class TripTrackingService {
    private final TripRepository trips;
    private final TripLocationUpdateRepository points;
    private final TripTrackingSessionRepository sessions;
    private final JourneyEventRepository events;
    private final Clock clock;
    private final TripRoadRouteService roads;
    private final DriverAccessPolicy driverAccess;
    /** driverAccountId for a legacy account driver, driverSessionId for a code-login session; never both. */
    public record Session(UUID id, UUID tripId, UUID driverAccountId, UUID driverSessionId, String status,
            Instant startedAt, Instant endedAt, Instant lastReceivedAt) {
        static Session from(TripTrackingSession s) {
            return new Session(s.getId(),s.getTripId(),s.getDriverAccountId(),s.getDriverSessionId(),s.getStatus(),s.getStartedAt(),s.getEndedAt(),s.getLastReceivedAt());
        }
        Session stopped() { return new Session(id,tripId,driverAccountId,driverSessionId,"STOPPED",startedAt,endedAt,lastReceivedAt); }
    }
    private static UUID actorOf(TripTrackingSession s) { return s.getDriverSessionId()!=null ? s.getDriverSessionId() : s.getDriverAccountId(); }
    public record Batch(List<UUID> accepted,List<UUID> duplicates,List<UUID> dropped) {}
    public record History(List<TripLocationResponse> points,String nextCursor,boolean hasMore,boolean sampled,long totalPoints) {
        public History(List<TripLocationResponse> points,String nextCursor,boolean hasMore) {
            this(points,nextCursor,hasMore,false,points.size());
        }
    }
    public record Snapshot(UUID tripId,TripStatus status,TripRoutePoint pickupPoint,TripRoutePoint deliveryPoint,
            long routeVersion,TripLocationResponse latestLocation,Session trackingSession,String freshness,
            Instant lastEventAt,Object routeGeometry,String routingStatus,Map<String,Object> routeMeta) {}

    @Transactional public Session start(UUID actor,AccountRole role,UUID id) {
        Trip trip=locked(id); driver(trip,actor,role); activeTrip(trip);
        var existing=sessions.findFirstByTripIdAndStatus(id,"ACTIVE");
        if (existing.isPresent() && actor.equals(actorOf(existing.get()))) return Session.from(existing.get());
        Instant now=clock.instant();
        boolean codeSession=DriverAccessPolicy.isDriverSession();
        if (codeSession) sessions.stopForDriverSession(actor,now); else sessions.stopForDriver(actor,now);
        sessions.stopForTrip(id,now);
        var session=TripTrackingSession.builder().id(UUID.randomUUID()).tripId(id)
                .driverAccountId(codeSession?null:actor).driverSessionId(codeSession?actor:null)
                .status("ACTIVE").startedAt(now).build();
        return Session.from(sessions.saveAndFlush(session));
    }
    @Transactional public Session stop(UUID actor,AccountRole role,UUID tripId,UUID sessionId) {
        var session=session(sessionId);
        if (role!=AccountRole.DRIVER || !actor.equals(actorOf(session)) || !tripId.equals(session.getTripId()))
            throw error(HttpStatus.FORBIDDEN,"Tracking session is not owned by this driver");
        if ("ACTIVE".equals(session.getStatus())) {
            session.setStatus("STOPPED");session.setEndedAt(clock.instant());sessions.save(session);
        }
        return Session.from(session);
    }
    @Transactional public Batch upload(UUID actor,AccountRole role,UUID tripId,GpsBatchRequest request) {
        Trip trip=locked(tripId);driver(trip,actor,role);activeTrip(trip);
        if (request==null || request.trackingSessionId()==null || request.points()==null
                || request.points().isEmpty() || request.points().size()>50) throw error(HttpStatus.BAD_REQUEST,"GPS batch must contain 1 to 50 points");
        var session=session(request.trackingSessionId());
        if (!tripId.equals(session.getTripId()) || !actor.equals(actorOf(session)) || !"ACTIVE".equals(session.getStatus()))
            throw error(HttpStatus.CONFLICT,"Tracking session is no longer active for this trip");
        Instant now=clock.instant();
        Map<UUID,GpsBatchRequest.Point> seen=new LinkedHashMap<>();
        Set<UUID> duplicates=new LinkedHashSet<>(),dropped=new LinkedHashSet<>();
        List<GpsBatchRequest.Point> accepted=new ArrayList<>();
        for (var point:request.points()) {
            boolean usable=TripGpsPolicy.validate(point,now);
            var earlier=seen.putIfAbsent(point.sampleId(),point);
            if (earlier!=null) {
                if (!earlier.equals(point)) throw error(HttpStatus.CONFLICT,"GPS sample ID has different data");
                duplicates.add(point.sampleId());continue;
            }
            var old=points.findByTrackingSessionIdAndSampleId(session.getId(),point.sampleId());
            if (old.isPresent()) {
                if (!same(old.get(),point)) throw error(HttpStatus.CONFLICT,"GPS sample ID has different data");
                duplicates.add(point.sampleId());
            } else if (!usable || point.capturedAt().isBefore(session.getStartedAt())) dropped.add(point.sampleId());
            else accepted.add(point);
        }
        if (!accepted.isEmpty() && session.getLastReceivedAt()!=null && session.getLastReceivedAt().isAfter(now.minusSeconds(1)))
            throw error(HttpStatus.TOO_MANY_REQUESTS,"GPS upload is too frequent; retry later");
        for (var point:accepted) {
            points.save(TripLocationUpdate.builder().trip(trip).actorId(actor).actorRole(role).source(TripLocationSource.GPS)
                    .actorType(session.getDriverSessionId()!=null?"DRIVER_SESSION":"ACCOUNT")
                    .status(trip.getStatus()).trackingSessionId(session.getId()).sampleId(point.sampleId())
                    .latitude(point.latitude()).longitude(point.longitude()).capturedAt(point.capturedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
                    .accuracyMeters(point.accuracyMeters()).build());
        }
        if (!accepted.isEmpty()) {session.setLastReceivedAt(now);sessions.save(session);}
        return new Batch(accepted.stream().map(GpsBatchRequest.Point::sampleId).toList(),List.copyOf(duplicates),List.copyOf(dropped));
    }
    @Transactional(readOnly=true) public Snapshot snapshot(UUID actor,AccountRole role,UUID id) {
        Trip trip=read(id);access(trip,actor,role);
        UUID currentDriver=DriverAccessPolicy.currentDriverActor(trip);
        var location=currentDriver==null?null:points.latestValid(id,currentDriver,PageRequest.of(0,1)).stream().findFirst().orElse(null);
        var session=sessions.findFirstByTripIdOrderByStartedAtDesc(id).orElse(null);
        boolean terminal=terminal(trip.getStatus());
        boolean stopped=terminal || (session!=null && (!"ACTIVE".equals(session.getStatus()) || !Objects.equals(actorOf(session),currentDriver)));
        var event=events.findFirstByTripIdOrderByRecordedAtDesc(id);
        var from=TripRoutePoint.from(trip.getPickupPoint());var to=TripRoutePoint.from(trip.getDeliveryPoint());
        // Cache read only: the provider is called on a background thread, never inside this transaction.
        var route=roads.lookup(from,to);
        return new Snapshot(id,trip.getStatus(),from,to,trip.getRouteVersion(),
                location==null?null:TripLocationResponse.from(location),session==null?null:(stopped?Session.from(session).stopped():Session.from(session)),
                TripGpsPolicy.freshness(location==null?null:captureTime(location),stopped,clock.instant()),
                event==null?null:event.getRecordedAt(),
                route==null?null:route.geometry(),route==null?null:route.status().name(),route==null?null:route.meta());
    }
    @Transactional(readOnly=true) public History history(UUID actor,AccountRole role,UUID id,int limit,String cursor) {
        access(read(id),actor,role);
        if (limit<1 || limit>1000) throw error(HttpStatus.BAD_REQUEST,"Location history limit must be 1 to 1000");
        List<TripLocationUpdate> rows;
        boolean more=false;
        long total=points.countByTripId(id);
        boolean sampled=false;
        if (cursor==null || cursor.isBlank()) {
            rows=new ArrayList<>(points.overview(id,limit));
            sampled=total>rows.size();
        } else {
            var parsed=decode(cursor);
            rows=new ArrayList<>(points.after(id,parsed.sequence(),PageRequest.of(0,limit+1)));
            more=rows.size()>limit;if(more)rows=new ArrayList<>(rows.subList(0,limit));
        }
        String next=rows.isEmpty()?cursor:encode(rows.get(rows.size()-1));
        rows.sort(Comparator.comparing(TripTrackingService::captureTime).thenComparing(TripLocationUpdate::getId));
        return new History(rows.stream().map(TripLocationResponse::from).toList(),next,more,sampled,total);
    }
    private static boolean same(TripLocationUpdate old,GpsBatchRequest.Point point) {
        return Objects.equals(old.getCapturedAt(),point.capturedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)) && Objects.equals(old.getLatitude(),point.latitude())
                && Objects.equals(old.getLongitude(),point.longitude()) && Objects.equals(old.getAccuracyMeters(),point.accuracyMeters());
    }
    private record Cursor(long sequence) {}
    private static Cursor decode(String raw) {
        try {
            if(raw.length()>128)throw new IllegalArgumentException();
            long sequence=Long.parseLong(new String(Base64.getUrlDecoder().decode(raw),StandardCharsets.UTF_8));
            if(sequence<1)throw new IllegalArgumentException();
            return new Cursor(sequence);
        } catch(Exception invalid){throw error(HttpStatus.BAD_REQUEST,"Invalid location cursor");}
    }
    private static String encode(TripLocationUpdate point) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(point.getIngestionSequence().toString().getBytes(StandardCharsets.UTF_8));
    }
    static Instant captureTime(TripLocationUpdate location) {return location.getCapturedAt()==null?location.getRecordedAt():location.getCapturedAt();}
    private Trip locked(UUID id){return trips.findByIdForUpdate(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Trip not found"));}
    private Trip read(UUID id){return trips.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Trip not found"));}
    private TripTrackingSession session(UUID id){return sessions.lockById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Tracking session not found"));}
    private void driver(Trip trip,UUID actor,AccountRole role) {
        if(!driverAccess.allows(trip,actor,role,DriverAccessPolicy.Mode.WRITE))throw error(HttpStatus.FORBIDDEN,"Assigned driver required");
    }
    private void access(Trip trip,UUID actor,AccountRole role) {
        if (!(role==AccountRole.ADMIN || role==AccountRole.SHIPPER && actor.equals(trip.getShipperId())
                || role==AccountRole.CARRIER && actor.equals(trip.getCarrierId()) || driverAccess.allows(trip,actor,role,DriverAccessPolicy.Mode.READ)))
            throw error(HttpStatus.FORBIDDEN,"You do not have access to this trip");
    }
    private static void activeTrip(Trip trip){if(terminal(trip.getStatus()))throw error(HttpStatus.CONFLICT,"GPS is stopped for delivered or closed trips");}
    static boolean terminal(TripStatus status){return status==TripStatus.DELIVERED || status==TripStatus.COMPLETED || status==TripStatus.CANCELLED;}
    private static ResponseStatusException error(HttpStatus status,String reason){return new ResponseStatusException(status,reason);}
}
