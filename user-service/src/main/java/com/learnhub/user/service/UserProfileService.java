package com.learnhub.user.service;

import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.user.dto.request.UpdateProfileRequest;
import com.learnhub.user.dto.response.UserProfileResponse;
import com.learnhub.user.entity.UserProfile;
import com.learnhub.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("PROFILE NOT FOUND", "not found profile of user: " + userId));

        return toResponse(profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("PROFILE_NOT_FOUND",
                        "Không tìm thấy profile cho user: " + userId));

        if(request.getFullName() != null) profile.setFullname(request.getFullName());
        if(request.getAvatarUrl() != null) profile.setAvatarUrl(request.getAvatarUrl());
        if(request.getBio() != null) profile.setBio(request.getBio());
        if(request.getHeadline() != null) profile.setHeadline(request.getHeadline());
        if(request.getWebsiteUrl() != null) profile.setWebsiteUrl(request.getWebsiteUrl());
        if(request.getPhoneNumber() != null) profile.setPhoneNumber(request.getPhoneNumber());
        if(request.getLanguage() != null) profile.setLanguage(request.getLanguage());
        if(request.getTimezone() != null) profile.setTimezone(request.getTimezone());

        return toResponse(userProfileRepository.save(profile));
    }

    private UserProfileResponse toResponse(UserProfile profile) {
        return UserProfileResponse.builder()
                .userId(profile.getUserId().toString())
                .fullName(profile.getFullname())
                .avatarUrl(profile.getAvatarUrl())
                .bio(profile.getBio())
                .headline(profile.getHeadline())
                .websiteUrl(profile.getWebsiteUrl())
                .language(profile.getLanguage())
                .timezone(profile.getTimezone())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
