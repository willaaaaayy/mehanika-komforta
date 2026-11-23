package com.mehanika.bot.lead;

public interface CrmLeadSink {
    enum Result {
        SENT, REJECTED, UNAVAILABLE
    }

    Result forward(String name, String phone, String objectType);

    default void remember(String createdAt) {
    }
}
