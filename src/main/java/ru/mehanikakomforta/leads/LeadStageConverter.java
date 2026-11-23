package ru.mehanikakomforta.leads;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class LeadStageConverter implements AttributeConverter<LeadStage, String> {

    @Override
    public String convertToDatabaseColumn(LeadStage stage) {
        return stage == null ? null : stage.code();
    }

    @Override
    public LeadStage convertToEntityAttribute(String raw) {
        return LeadStage.parse(raw);
    }
}
