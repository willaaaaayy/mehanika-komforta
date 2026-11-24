package ru.mehanikakomforta.leads;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LeadResponse(
        Long id,
        Instant createdAt,
        String name,
        String phone,
        String objectType,
        String source,
        String stage,
        String section,
        BigDecimal floorArea,
        String grade,
        String condition,
        String packageCode,
        List<String> works,
        String city,
        Integer electro,
        Integer water,
        Integer cameras,
        Integer lan,
        Boolean chase,
        BigDecimal heightM,
        List<String> complexItems,
        String complexNote,
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
                lead.getSection(),
                lead.getFloorArea(),
                lead.getGrade(),
                lead.getConditionCode(),
                lead.getPackageCode(),
                SurveyText.list(lead.getWorksJson()),
                lead.getCity(),
                lead.getElectro(),
                lead.getWater(),
                lead.getCameras(),
                lead.getLan(),
                lead.getChase(),
                lead.getHeightM(),
                SurveyText.list(lead.getComplexItems()),
                lead.getComplexNote(),
                telegram
        );
    }
}
