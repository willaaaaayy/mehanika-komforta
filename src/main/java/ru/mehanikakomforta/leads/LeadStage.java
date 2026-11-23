package ru.mehanikakomforta.leads;

public enum LeadStage {
    new_,
    measure,
    contract,
    work,
    done,
    warranty;

    public String code() {
        return this == new_ ? "new" : name();
    }

    public static LeadStage parse(String raw) {
        if (raw == null) {
            return null;
        }
        for (LeadStage stage : values()) {
            if (stage.code().equals(raw)) {
                return stage;
            }
        }
        return null;
    }
}
