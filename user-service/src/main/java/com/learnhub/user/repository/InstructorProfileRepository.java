package com.learnhub.user.repository;

import com.learnhub.user.entity.InstructorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstructorProfileRepository extends JpaRepository<InstructorProfile, UUID> {
    Optional<InstructorProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
