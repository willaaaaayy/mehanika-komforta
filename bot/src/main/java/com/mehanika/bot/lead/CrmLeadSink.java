package com.mehanika.bot.lead;

import java.util.List;

public interface CrmLeadSink {
    enum Result {
        SENT, REJECTED, UNAVAILABLE
    }

    Result forward(String name, String phone, String objectType);

    default Result deliver(String name, String phone, String objectType, TelegramProfile profile, List<LeadPhoto> photos) {
        return forward(name, phone, objectType);
    }

    default void remember(String createdAt) {
    }
}
