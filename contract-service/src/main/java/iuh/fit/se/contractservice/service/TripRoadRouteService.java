package iuh.fit.se.contractservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.contractservice.dto.TripRoutePoint;
import iuh.fit.se.contractservice.service.routing.CountryBoundaries;
import iuh.fit.se.contractservice.service.routing.RouteGeometryValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/**
 * Optional planned road between the two warehouse pins; never evidence of the vehicle's movement.
 *
 * Tracking polls only read the cache and get a status back immediately. Fetching happens on one
 * background thread: requests for the same key are deduplicated, the provider sees at most one request
 * per second, failures and 429 back off, and a cached geometry is only reused for the same anchor pair,
 * provider, profile and validation policy.
 */
@Service
public class TripRoadRouteService {
    public enum Status { READY, PENDING, UNAVAILABLE, NO_ROUTE, REJECTED, NOT_CONFIGURED }

    public record RouteResult(Status status, Map<String, Object> geometry, Map<String, Object> meta) {
        static RouteResult of(Status status) {
            return new RouteResult(status, null, Map.of());
        }
    }

    /** OSRM profile in use. The public demo only offers car routing, not truck restrictions. */
    static final String PROFILE = "driving";
    /** Bump when validation or simplification rules change so old cached routes are not reused. */
    static final String POLICY = "v2-dp15m-border2km";
    static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
    private static final Duration READY_TTL = Duration.ofHours(6);
    private static final Duration NO_ROUTE_TTL = Duration.ofHours(1);
    private static final Duration MIN_INTERVAL = Duration.ofMillis(1100);
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);
    private static final int CACHE_LIMIT = 256;

    private final String baseUrl;
    private final ObjectMapper json;
    private final RouteGeometryValidator validator;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "road-route-fetch");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private Instant lastRequestAt = Instant.EPOCH;

    private record Entry(RouteResult result, Instant expiresAt, Instant retryAt, int failures) {
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TripRoadRouteService(@Value("${app.tracking.routing-base-url:}") String baseUrl, ObjectMapper json) {
        this(baseUrl, json, new RouteGeometryValidator(CountryBoundaries.load(json)), Clock.systemUTC());
    }

    TripRoadRouteService(String baseUrl, ObjectMapper json, RouteGeometryValidator validator, Clock clock) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.json = json;
        this.validator = validator;
        this.clock = clock;
        if (!this.baseUrl.isBlank()) {
            URI uri = URI.create(this.baseUrl);
            if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Invalid routing provider configuration");
            }
        }
    }

    /** Non-blocking: returns what is known now and schedules a fetch when needed. */
    public RouteResult lookup(TripRoutePoint from, TripRoutePoint to) {
        if (!usable(from) || !usable(to)) return null;
        if (baseUrl.isBlank()) return RouteResult.of(Status.NOT_CONFIGURED);
        String key = key(from, to);
        Instant now = clock.instant();
        Entry entry;
        synchronized (cache) {
            entry = cache.get(key);
        }
        boolean fresh = entry != null && entry.expiresAt() != null && entry.expiresAt().isAfter(now);
        boolean mayRetry = entry == null || entry.retryAt() == null || !entry.retryAt().isAfter(now);
        if (!fresh && mayRetry) schedule(key, from, to);
        if (entry == null) return RouteResult.of(Status.PENDING);
        return entry.result();
    }

    private void schedule(String key, TripRoutePoint from, TripRoutePoint to) {
        if (!inFlight.add(key)) return;
        try {
            worker.execute(() -> {
                try {
                    fetch(key, from, to);
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (RuntimeException rejected) {
            inFlight.remove(key);
        }
    }

    void fetch(String key, TripRoutePoint from, TripRoutePoint to) {
        pace();
        Entry previous;
        synchronized (cache) {
            previous = cache.get(key);
        }
        int failures = previous == null ? 0 : previous.failures();
        Instant now = clock.instant();
        try {
            URI url = URI.create(baseUrl + "/route/v1/" + PROFILE + "/" + coordinates(from, to)
                    + "?overview=full&geometries=geojson&steps=false&alternatives=3");
            var pending = http.sendAsync(HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(4)).GET().build(),
                    ignored -> new BoundedBody());
            HttpResponse<byte[]> response;
            try {
                response = pending.get(5, TimeUnit.SECONDS);
            } finally {
                if (!pending.isDone()) pending.cancel(true);
            }
            if (response.statusCode() == 429) {
                Duration wait = retryAfter(response).orElse(backoff(failures));
                store(key, unavailable(previous, "RATE_LIMITED"), null, now.plus(wait), failures + 1);
                return;
            }
            if (response.statusCode() >= 500) {
                store(key, unavailable(previous, "PROVIDER_ERROR"), null, now.plus(backoff(failures)), failures + 1);
                return;
            }
            JsonNode body = json.readTree(response.body());
            String code = body.path("code").asText();
            if ("NoRoute".equals(code) || "NoSegment".equals(code)) {
                store(key, new RouteResult(Status.NO_ROUTE, null, meta(code, null)), now.plus(NO_ROUTE_TTL), null, 0);
                return;
            }
            if (!"Ok".equals(code)) {
                store(key, unavailable(previous, "PROVIDER_" + code.toUpperCase(Locale.ROOT)), null, now.plus(backoff(failures)), failures + 1);
                return;
            }
            store(key, choose(body, from, to), now.plus(READY_TTL), null, 0);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception unavailable) {
            // Timeout, connection failure or oversized body: tracking keeps working with pins only.
            store(key, unavailable(previous, "UNREACHABLE"), null, now.plus(backoff(failures)), failures + 1);
        }
    }

    /** First provider alternative that passes validation; otherwise the first rejection reason. */
    RouteResult choose(JsonNode body, TripRoutePoint from, TripRoutePoint to) {
        double[] a = {from.longitude(), from.latitude()};
        double[] b = {to.longitude(), to.latitude()};
        String firstReason = null;
        JsonNode routes = body.path("routes");
        for (int index = 0; index < routes.size(); index++) {
            JsonNode route = routes.get(index);
            var parsed = RouteGeometryValidator.parse(route.path("geometry"));
            if (parsed.isEmpty()) {
                if (firstReason == null) firstReason = "MALFORMED_GEOMETRY";
                continue;
            }
            var outcome = validator.validate(parsed.get(), a, b);
            if (outcome instanceof RouteGeometryValidator.Accepted accepted) {
                List<List<Double>> coordinates = new ArrayList<>(accepted.points().size());
                for (double[] point : accepted.points()) coordinates.add(List.of(point[0], point[1]));
                Map<String, Object> meta = meta(null, index);
                meta.put("distanceMeters", Math.round(accepted.lengthMeters()));
                meta.put("toleranceMeters", accepted.toleranceMeters());
                return new RouteResult(Status.READY, Map.of("type", "LineString", "coordinates", coordinates), meta);
            }
            if (firstReason == null) firstReason = ((RouteGeometryValidator.Rejected) outcome).reason();
        }
        if (routes.isEmpty()) return new RouteResult(Status.NO_ROUTE, null, meta("EMPTY", null));
        return new RouteResult(Status.REJECTED, null, meta(firstReason, null));
    }

    private Map<String, Object> meta(String reason, Integer alternative) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("provider", URI.create(baseUrl).getHost());
        meta.put("profile", PROFILE);
        meta.put("policy", POLICY);
        if (reason != null) meta.put("reason", reason);
        if (alternative != null) meta.put("alternative", alternative);
        return meta;
    }

    /** Keeps a still-valid READY route while the provider is temporarily failing. */
    private RouteResult unavailable(Entry previous, String reason) {
        if (previous != null && previous.result().status() == Status.READY) return previous.result();
        return new RouteResult(Status.UNAVAILABLE, null, meta(reason, null));
    }

    private void store(String key, RouteResult result, Instant expiresAt, Instant retryAt, int failures) {
        synchronized (cache) {
            Entry previous = cache.get(key);
            // A failure while a READY route is cached keeps that route until it expires.
            Instant keep = result.status() == Status.READY && previous != null && previous.result() == result
                    ? previous.expiresAt() : expiresAt;
            cache.put(key, new Entry(result, keep, retryAt, failures));
            while (cache.size() > CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
        }
    }

    private void pace() {
        Instant earliest = lastRequestAt.plus(MIN_INTERVAL);
        Instant now = clock.instant();
        if (earliest.isAfter(now)) {
            try {
                Thread.sleep(Duration.between(now, earliest).toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = clock.instant();
    }

    static Duration backoff(int failures) {
        long seconds = FIRST_BACKOFF.getSeconds() << Math.min(failures, 5);
        return Duration.ofSeconds(Math.min(seconds, MAX_BACKOFF.getSeconds()));
    }

    private static java.util.Optional<Duration> retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                long seconds = Long.parseLong(value.trim());
                return java.util.Optional.of(Duration.ofSeconds(Math.max(1, Math.min(seconds, MAX_BACKOFF.getSeconds()))));
            } catch (NumberFormatException notSeconds) {
                return java.util.Optional.empty();
            }
        });
    }

    private static boolean usable(TripRoutePoint point) {
        return point != null && point.latitude() != null && point.longitude() != null && point.isCoordinatePairValid();
    }

    String key(TripRoutePoint from, TripRoutePoint to) {
        return POLICY + "|" + baseUrl + "|" + PROFILE + "|" + coordinates(from, to);
    }

    private static String coordinates(TripRoutePoint from, TripRoutePoint to) {
        return String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", from.longitude(), from.latitude(), to.longitude(), to.latitude());
    }

    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        public CompletionStage<byte[]> getBody() {
            return result;
        }

        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if ((long) bytes.size() + buffer.remaining() > MAX_BODY_BYTES) {
                    subscription.cancel();
                    result.completeExceptionally(new IllegalArgumentException("Routing response is too large"));
                    return;
                }
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }

        public void onError(Throwable failure) {
            result.completeExceptionally(failure);
        }

        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }
}
