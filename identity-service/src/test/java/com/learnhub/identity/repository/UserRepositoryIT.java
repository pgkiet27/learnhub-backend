package com.learnhub.identity.repository;

import com.learnhub.identity.entity.User;
import com.learnhub.identity.identity.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DisplayName("UserRepository Integration Tests")
public class UserRepositoryIT extends AbstractIntegrationTest {
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("findByCognitoSub - User exists - return User")
    void findByCognitoSub_UserExists_ShouldReturnUser(){
        // Given
        User user = User.builder()
                .cognitoSub("cognito-sub-test-001")
                .email("integration-test@example.com")
                .role(User.Role.student)
                .build();
        userRepository.save(user);

        // When
        Optional<User> found = userRepository.findByCognitoSub("cognito-sub-test-001");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("integration-test@example.com");
        assertThat(found.get().getRole()).isEqualTo(User.Role.student);
    }

    @Test
    @DisplayName("save - create new user - ID generate automatically")
    void save_NewUser_ShouldAutoGenerateId() {
        // Given
        User user = User.builder()
                .cognitoSub("cognito-sub-test-002")
                .email("test2@example.com")
                .role(User.Role.instructor)
                .build();

        // When
        User saved = userRepository.save(user);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(User.Role.instructor);
    }
}
