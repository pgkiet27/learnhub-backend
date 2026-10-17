package com.learnhub.course.repository;

import com.learnhub.course.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SectionRepository extends JpaRepository<Section, UUID> {

    List<Section> findByCourseIdOrderByDisplayOrderAsc(UUID courseId);

    // Get the highest display_order in the course to add a new section
    @Query("SELECT COALESCE(MAX(s.displayOrder), 0) FROM Section s WHERE s.course.id = :courseId")
    Integer findMaxDisplayOrderByCourseId(UUID courseId);

    // Delete all sections of a course
    @Modifying
    @Query("DELETE FROM Section s WHERE s.course.id = :courseId")
    void deleteAllByCourseId(UUID courseId);
}