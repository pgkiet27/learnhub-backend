package com.learnhub.user.dto.request;

import com.learnhub.user.entity.SupportTicket;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSupportTicketRequest {
    // null = keep the current status
    private SupportTicket.Status status;

    @Size(max = 5000)
    private String reply;
}
