package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Complaint;
import iuh.fit.se.contractservice.domain.entity.ComplaintMessage;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ComplaintStatus;
import iuh.fit.se.contractservice.dto.AddComplaintMessageRequest;
import iuh.fit.se.contractservice.dto.ComplaintResponse;
import iuh.fit.se.contractservice.dto.CreateComplaintRequest;
import iuh.fit.se.contractservice.dto.ResolveComplaintRequest;
import iuh.fit.se.contractservice.repository.ComplaintRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComplaintService {
    private final ComplaintRepository complaintRepository;
    private final TripRepository tripRepository;
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final Map<UUID, Object> subscriberLocks = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<ComplaintResponse> list(UUID accountId, AccountRole role) {
        List<Complaint> complaints = role == AccountRole.ADMIN
                ? complaintRepository.findAllByOrderByCreatedAtDesc()
                : complaintRepository.findByReporterIdOrRespondentIdOrderByCreatedAtDesc(accountId, accountId);
        return complaints.stream().map(ComplaintResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintResponse get(UUID accountId, AccountRole role, UUID complaintId) {
        return ComplaintResponse.from(loadAccessible(accountId, role, complaintId));
    }

    @Transactional
    public ComplaintResponse create(UUID accountId, AccountRole role, CreateComplaintRequest request) {
        requireParty(role);
        Trip trip = tripRepository.findById(request.tripId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        UUID respondentId;
        if (accountId.equals(trip.getShipperId())) {
            respondentId = trip.getCarrierId();
        } else if (accountId.equals(trip.getCarrierId())) {
            respondentId = trip.getShipperId();
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this trip");
        }
        Complaint complaint = Complaint.builder()
                .tripId(trip.getId())
                .reporterId(accountId)
                .respondentId(respondentId)
                .title(request.title().trim())
                .description(request.description().trim())
                .evidenceUrl(normalizeOptional(request.evidenceUrl()))
                .status(ComplaintStatus.PENDING)
                .build();
        return ComplaintResponse.from(complaintRepository.save(complaint));
    }

    @Transactional
    public ComplaintResponse addMessage(UUID accountId, AccountRole role, UUID complaintId, AddComplaintMessageRequest request) {
        Complaint complaint = loadAccessible(accountId, role, complaintId);
        ensureOpen(complaint);
        complaint.getMessages().add(ComplaintMessage.builder()
                .complaint(complaint)
                .senderId(accountId)
                .senderRole(role)
                .message(request.message().trim())
                .createdAt(Instant.now())
                .build());
        if (complaint.getStatus() == ComplaintStatus.PENDING) complaint.setStatus(ComplaintStatus.PROCESSING);
        ComplaintResponse response = ComplaintResponse.from(complaintRepository.save(complaint));
        publishAfterCommit(complaintId, response);
        return response;
    }

    @Transactional
    public ComplaintResponse resolve(UUID adminId, AccountRole role, UUID complaintId, ResolveComplaintRequest request) {
        if (role != AccountRole.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
        ensureOpen(complaint);
        complaint.setDecision(request.decision());
        complaint.setResolution(request.resolution().trim());
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.getMessages().add(ComplaintMessage.builder()
                .complaint(complaint)
                .senderId(adminId)
                .senderRole(AccountRole.ADMIN)
                .message(request.resolution().trim())
                .createdAt(Instant.now())
                .build());
        ComplaintResponse response = ComplaintResponse.from(complaintRepository.save(complaint));
        publishAfterCommit(complaintId, response);
        return response;
    }

    public SseEmitter subscribe(UUID accountId, AccountRole role, UUID complaintId) {
        ComplaintResponse current = get(accountId, role, complaintId);
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> complaintSubscribers = subscribers.computeIfAbsent(
                complaintId,
                ignored -> new CopyOnWriteArrayList<>());
        Runnable cleanup = () -> removeSubscriber(complaintId, emitter, complaintSubscribers);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        synchronized (subscriberLocks.computeIfAbsent(complaintId, ignored -> new Object())) {
            complaintSubscribers.add(emitter);
            try {
                sendUpdate(emitter, current);
            } catch (IOException exception) {
                cleanup.run();
                emitter.completeWithError(exception);
            }
        }
        return emitter;
    }

    private void publishAfterCommit(UUID complaintId, ComplaintResponse response) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(complaintId, response);
                }
            });
            return;
        }
        publish(complaintId, response);
    }

    private void publish(UUID complaintId, ComplaintResponse response) {
        CopyOnWriteArrayList<SseEmitter> complaintSubscribers = subscribers.get(complaintId);
        if (complaintSubscribers == null) return;

        synchronized (subscriberLocks.computeIfAbsent(complaintId, ignored -> new Object())) {
            for (SseEmitter emitter : complaintSubscribers) {
                try {
                    sendUpdate(emitter, response);
                } catch (IOException exception) {
                    complaintSubscribers.remove(emitter);
                    emitter.completeWithError(exception);
                }
            }
        }
    }

    private void sendUpdate(SseEmitter emitter, ComplaintResponse response) throws IOException {
        emitter.send(SseEmitter.event()
                .id(UUID.randomUUID().toString())
                .name("complaint.updated")
                .data(response));
    }

    private void removeSubscriber(UUID complaintId, SseEmitter emitter, CopyOnWriteArrayList<SseEmitter> complaintSubscribers) {
        complaintSubscribers.remove(emitter);
        if (complaintSubscribers.isEmpty()) subscribers.remove(complaintId, complaintSubscribers);
    }

    private Complaint loadAccessible(UUID accountId, AccountRole role, UUID complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (role != AccountRole.ADMIN && !accountId.equals(complaint.getReporterId()) && !accountId.equals(complaint.getRespondentId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this complaint");
        }
        return complaint;
    }

    private void requireParty(AccountRole role) {
        if (role != AccountRole.SHIPPER && role != AccountRole.CARRIER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Shipper or carrier role required");
        }
    }

    private void ensureOpen(Complaint complaint) {
        if (complaint.getStatus() == ComplaintStatus.RESOLVED || complaint.getStatus() == ComplaintStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complaint is already closed");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
