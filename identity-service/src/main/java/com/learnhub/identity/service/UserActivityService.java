package com.learnhub.identity.service;

import com.learnhub.identity.dto.response.UserActivityResponse;
import com.learnhub.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserActivityService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserActivityResponse> getActivity(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return userRepository.findActivity(userIds, today).stream()
                .map(row -> UserActivityResponse.builder()
                        .userId((UUID) row[0])
                        .email((String) row[1])
                        .lastActiveAt(toInstant(row[2]))
                        .activeDaysLast14(((Number) row[3]).intValue())
                        .activeDaysPrev14(((Number) row[4]).intValue())
                        .build())
                .toList();
    }

    private static Instant toInstant(Object value) {
        return switch (value) {
            case Instant i -> i;
            case Timestamp t -> t.toInstant();
            case LocalDateTime ldt -> ldt.toInstant(ZoneOffset.UTC);
            default -> throw new IllegalStateException("Unexpected timestamp type: " + value.getClass());
        };
    }
}
