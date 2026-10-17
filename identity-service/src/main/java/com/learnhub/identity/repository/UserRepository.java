package com.learnhub.identity.repository;

import com.learnhub.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByCognitoSub(String cognitoSub);
    Optional<User> findByEmail(String email);
    boolean existsByCognitoSub(String cognitoSub);
    boolean existsByEmail(String email);

    @Modifying
    @Query(value = "INSERT INTO user_login_days (user_id, login_date) VALUES (:userId, :date) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void recordLoginDay(@Param("userId") UUID userId, @Param("date") LocalDate date);

    /**
     * Rows: [id, email, last_active_at, active_days_last_14, active_days_prev_14].
     * last_active_at falls back to created_at for users with no recorded login yet.
     */
    @Query(value = """
            SELECT u.id, u.email, COALESCE(u.last_login_at, u.created_at),
                   COUNT(d.login_date) FILTER (WHERE d.login_date > CAST(:today AS date) - 14),
                   COUNT(d.login_date) FILTER (WHERE d.login_date <= CAST(:today AS date) - 14
                                                 AND d.login_date > CAST(:today AS date) - 28)
            FROM users u
            LEFT JOIN user_login_days d ON d.user_id = u.id
            WHERE u.id IN (:ids)
            GROUP BY u.id, u.email, u.last_login_at, u.created_at
            """, nativeQuery = true)
    List<Object[]> findActivity(@Param("ids") Collection<UUID> ids, @Param("today") LocalDate today);
}
