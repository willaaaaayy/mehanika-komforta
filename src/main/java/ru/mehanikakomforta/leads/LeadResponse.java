package ru.mehanikakomforta.leads;

import java.time.Instant;

public record LeadResponse(
        Long id,
        Instant createdAt,
        String name,
        String phone,
        String objectType,
        String source,
        String stage
) {
    public static LeadResponse from(LeadEntity lead) {
        return new LeadResponse(
                lead.getId(),
                lead.getCreatedAt(),
                lead.getName(),
                lead.getPhone(),
                lead.getObjectType(),
                lead.getSource().name(),
                lead.getStage().code()
        );
    }
}
