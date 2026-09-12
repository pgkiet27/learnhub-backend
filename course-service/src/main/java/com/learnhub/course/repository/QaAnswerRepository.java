package com.learnhub.course.repository;

import com.learnhub.course.entity.QaAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface QaAnswerRepository extends JpaRepository<QaAnswer, UUID> {

    @Modifying
    @Query("UPDATE QaAnswer a SET a.isAccepted = false WHERE a.question.id = :questionId")
    void clearAcceptedByQuestionId(UUID questionId);
}