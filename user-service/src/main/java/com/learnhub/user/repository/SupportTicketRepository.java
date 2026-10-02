package com.learnhub.user.repository;

import com.learnhub.user.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Page<SupportTicket> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<SupportTicket> findByStatusOrderByCreatedAtAsc(SupportTicket.Status status, Pageable pageable);

    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Rows: [user_id, ticket count] for users with at least one ticket since the given time. */
    @Query("""
            SELECT t.userId, COUNT(t) FROM SupportTicket t
            WHERE t.userId IN :userIds AND t.createdAt >= :since
            GROUP BY t.userId
            """)
    List<Object[]> countByUserSince(Collection<UUID> userIds, Instant since);
}
