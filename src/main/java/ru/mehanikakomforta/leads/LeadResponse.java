package ru.mehanikakomforta.leads;

import java.math.BigDecimal;
import java.time.Instant;

public record LeadResponse(
        Long id,
        Instant createdAt,
        String name,
        String phone,
        String objectType,
        String source,
        String stage,
        BigDecimal floorArea,
        String grade,
        String condition,
        String packageCode,
        TelegramView telegram
) {
    public record TelegramView(
            long id,
            String username,
            String firstName,
            String lastName,
            String bio,
            boolean photo
    ) {
    }

    public static LeadResponse from(LeadEntity lead) {
        return from(lead, false);
    }

    public static LeadResponse from(LeadEntity lead, boolean profilePhoto) {
        TelegramView telegram = lead.getTelegramId() == null ? null : new TelegramView(
                lead.getTelegramId(),
                lead.getTelegramUsername(),
                lead.getTelegramFirstName(),
                lead.getTelegramLastName(),
                lead.getTelegramBio(),
                profilePhoto
        );
        return new LeadResponse(
                lead.getId(),
                lead.getCreatedAt(),
                lead.getName(),
                lead.getPhone(),
                lead.getObjectType(),
                lead.getSource().name(),
                lead.getStage().code(),
                lead.getFloorArea(),
                lead.getGrade(),
                lead.getConditionCode(),
                lead.getPackageCode(),
                telegram
        );
    }
}
