package com.learnhub.user.dto.request;

import com.learnhub.user.entity.SupportTicket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateSupportTicketRequest {
    @NotNull
    private SupportTicket.Category category;

    @NotBlank
    @Size(max = 200)
    private String subject;

    @NotBlank
    @Size(max = 5000)
    private String message;

    private UUID courseId;
}
