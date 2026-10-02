package com.learnhub.user.service;

import com.learnhub.common.dto.PageResponse;
import com.learnhub.common.exception.ForbiddenException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.user.dto.request.CreateSupportTicketRequest;
import com.learnhub.user.dto.request.UpdateSupportTicketRequest;
import com.learnhub.user.dto.response.SupportTicketResponse;
import com.learnhub.user.entity.SupportTicket;
import com.learnhub.user.entity.UserProfile;
import com.learnhub.user.repository.SupportTicketRepository;
import com.learnhub.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupportTicketService {

    // Window for the churn feature support_tickets_opened
    public static final Duration CHURN_WINDOW = Duration.ofDays(30);

    private final SupportTicketRepository ticketRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public SupportTicketResponse create(UUID userId, CreateSupportTicketRequest request) {
        SupportTicket ticket = ticketRepository.save(SupportTicket.builder()
                .userId(userId)
                .courseId(request.getCourseId())
                .category(request.getCategory())
                .subject(request.getSubject().strip())
                .message(request.getMessage().strip())
                .build());
        return toResponse(ticket, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketResponse> listMine(UUID userId, Pageable pageable) {
        return PageResponse.of(ticketRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(t -> toResponse(t, null)));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketResponse> listAll(String role, SupportTicket.Status status, Pageable pageable) {
        requireAdmin(role);
        Page<SupportTicket> tickets = status == null
                ? ticketRepository.findAllByOrderByCreatedAtDesc(pageable)
                : ticketRepository.findByStatusOrderByCreatedAtAsc(status, pageable);
        Map<UUID, String> names = userProfileRepository
                .findByUserIdIn(tickets.map(SupportTicket::getUserId).toSet()).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, UserProfile::getFullname));
        return PageResponse.of(tickets.map(t -> toResponse(t, names.get(t.getUserId()))));
    }

    @Transactional
    public SupportTicketResponse update(UUID adminId, String role, UUID ticketId, UpdateSupportTicketRequest request) {
        requireAdmin(role);
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("TICKET_NOT_FOUND", "Ticket không tồn tại"));
        if (request.getReply() != null && !request.getReply().isBlank()) {
            ticket.setAdminReply(request.getReply().strip());
            ticket.setRepliedBy(adminId);
            ticket.setRepliedAt(Instant.now());
            if (request.getStatus() == null && ticket.getStatus() == SupportTicket.Status.open) {
                ticket.setStatus(SupportTicket.Status.in_progress);
            }
        }
        if (request.getStatus() != null) {
            ticket.setStatus(request.getStatus());
        }
        return toResponse(ticketRepository.save(ticket), null);
    }

    /** Tickets opened in the churn window, per user; users without tickets map to 0. */
    @Transactional(readOnly = true)
    public Map<UUID, Long> countRecent(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>(userIds.stream().distinct()
                .collect(Collectors.toMap(Function.identity(), id -> 0L)));
        for (Object[] row : ticketRepository.countByUserSince(userIds, Instant.now().minus(CHURN_WINDOW))) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static void requireAdmin(String role) {
        if (!"admin".equals(role)) {
            throw new ForbiddenException("ADMIN_ONLY", "Chỉ admin mới được quản lý ticket hỗ trợ");
        }
    }

    private static SupportTicketResponse toResponse(SupportTicket t, String userFullName) {
        return new SupportTicketResponse(t.getId(), t.getUserId(), userFullName, t.getCourseId(),
                t.getCategory().name(), t.getSubject(), t.getMessage(), t.getStatus().name(),
                t.getAdminReply(), t.getRepliedAt(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
