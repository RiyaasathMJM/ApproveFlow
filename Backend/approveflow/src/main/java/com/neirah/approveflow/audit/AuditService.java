package com.neirah.approveflow.audit;

import com.neirah.approveflow.request.RequestStatus;
import com.neirah.approveflow.user.User;
import com.neirah.approveflow.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private final AuditEventRepository events;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public AuditService(AuditEventRepository events, UserRepository users, ObjectMapper mapper) {
        this.events = events;
        this.users = users;
        this.mapper = mapper;
    }

    // MANDATORY: audit write thaniya nadakkaadhu, business transaction-kulla mattum
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long requestId, Long actorId, AuditAction action,
                       RequestStatus from, RequestStatus to,
                       String comment, Map<String, Object> metadata) {
        AuditEvent e = new AuditEvent();
        e.setRequestId(requestId);
        e.setActorId(actorId);
        e.setAction(action);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setComment(comment);
        e.setMetadataJson(metadata == null ? null : mapper.writeValueAsString(metadata));
        events.save(e);
    }

    @Transactional(readOnly = true)
    public List<TimelineEvent> timeline(Long requestId) {
        List<AuditEvent> list = events.findByRequestIdOrderByCreatedAtAscIdAsc(requestId);
        List<Long> actorIds = list.stream().map(AuditEvent::getActorId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = users.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        return list.stream().map(e -> new TimelineEvent(
                e.getId(), e.getAction(), e.getActorId(),
                e.getActorId() == null ? "System" : names.getOrDefault(e.getActorId(), "Unknown"),
                e.getFromStatus(), e.getToStatus(), e.getComment(), e.getCreatedAt())).toList();
    }
}