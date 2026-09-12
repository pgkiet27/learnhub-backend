package com.learnhub.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(max = 100, message = "Name cannot over 100 characters")
    private String fullName;

    @Size(max = 500, message = "Invalid avatar URL")
    private String avatarUrl;

    @Size(max = 1000, message = "Bio do not over 1000 characters")
    private String bio;

    @Size(max = 200, message = "Headline do not over 200 characters")
    private String headline;

    @Size(max = 255, message = "Invalid website URL")
    private String websiteUrl;

    @Size(max = 20, message = "Invalid phone number")
    private String phoneNumber;

    private String language;
    private String timezone;
}
