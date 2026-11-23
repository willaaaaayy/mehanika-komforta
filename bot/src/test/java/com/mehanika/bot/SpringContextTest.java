package com.mehanika.bot;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.mehanika.bot.dialog.Dialog;
import com.mehanika.bot.dialog.InMessage;

@SpringBootTest(properties = {
        "BOT_TOKEN=test-token",
        "ADMIN_CHAT_ID=100",
        "bot.polling=false",
        "bot.catalog-path=classpath:prices.json",
        "bot.leads-path=target/spring-leads.jsonl"
})
class SpringContextTest {
    @Autowired
    Dialog dialog;

    @Test
    void startFromTheSpringContext() {
        assertFalse(dialog.onMessage(new InMessage(1, 1, 1, null, "/start")).isEmpty());
    }
}
