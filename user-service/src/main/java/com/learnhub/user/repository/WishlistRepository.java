package com.learnhub.user.repository;

import com.learnhub.user.entity.Wishlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, UUID> {

    Page<Wishlist> findByUserId(UUID userId, Pageable pageable);

    boolean existsByUserIdAndCourseId(UUID userId, UUID courseId);

    void deleteByUserIdAndCourseId(UUID userId, UUID courseId);
}
