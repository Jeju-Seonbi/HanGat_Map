package com.example.hangat.notification;

import com.example.hangat.config.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 알림의 페이지·분류·삭제는 로그인한 본인 데이터에만 적용한다. */
@SpringBootTest(properties = "hangat.notifications.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationInboxTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;

    private String auth() { return "Bearer " + jwt.createAccessToken(1L); }

    private long seed(long userId, String type, String key) {
        jdbc.update("INSERT INTO notifications (user_id,type,title,message,dedupe_key,created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
                userId, type, "제목", "내용", key);
        return jdbc.queryForObject("SELECT id FROM notifications WHERE user_id=? AND dedupe_key=?", Long.class, userId, key);
    }

    @Test
    void pageIsLimitedToSevenAndFilterAppliesBeforePagination() throws Exception {
        for (int i = 0; i < 9; i++) seed(1, "SECURITY_LOGIN", "login" + i);
        seed(1, "AI_COURSE_COMPLETED", "complete");
        seed(1, "AI_COURSE_FAILED", "failed");
        seed(2, "SECURITY_LOGIN", "other-user");
        mvc.perform(get("/users/me/notifications/page").header("Authorization", auth()).param("category", "LOGIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items.length()").value(7))
                .andExpect(jsonPath("$.result.totalElements").value(9)).andExpect(jsonPath("$.result.totalPages").value(2));
        mvc.perform(get("/users/me/notifications/page").header("Authorization", auth()).param("category", "LOGIN").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items.length()").value(2));
        mvc.perform(get("/users/me/notifications/page").header("Authorization", auth()).param("category", "COURSE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items.length()").value(2));
    }

    @Test
    void deletedNotificationsDisappearButKeepTheirDedupeRecord() throws Exception {
        long mine = seed(1, "SECURITY_LOGIN", "mine");
        long theirs = seed(2, "NOTICE", "theirs");
        mvc.perform(delete("/users/me/notifications/" + theirs).header("Authorization", auth())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM notifications WHERE id=?", java.sql.Timestamp.class, theirs)).isNull();
        mvc.perform(delete("/users/me/notifications/" + mine).header("Authorization", auth())).andExpect(status().isOk());
        mvc.perform(get("/users/me/notifications").header("Authorization", auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items.length()").value(0))
                .andExpect(jsonPath("$.result.unreadCount").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE id=? AND deleted_at IS NOT NULL AND dedupe_key='mine'", Integer.class, mine)).isEqualTo(1);
    }

    @Test
    void deleteAllIncludesOlderPagesButNotAnotherUsersNotifications() throws Exception {
        for (int i = 0; i < 12; i++) seed(1, "TRIP_SUMMARY", "trip" + i);
        seed(2, "NOTICE", "other");
        mvc.perform(delete("/users/me/notifications").header("Authorization", auth())).andExpect(status().isOk());
        mvc.perform(get("/users/me/notifications/page").header("Authorization", auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=2 AND deleted_at IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void unknownTypesRemainVisibleUnderOther() throws Exception {
        seed(1, "FUTURE_TYPE", "future");
        seed(1, "SECURITY_PASSWORD_CHANGED", "password");
        seed(1, "FORECAST_CHANGE", "weather");
        mvc.perform(get("/users/me/notifications/page").header("Authorization", auth()).param("category", "OTHER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(2));
        mvc.perform(get("/users/me/notifications/page").header("Authorization", auth()).param("category", "TRIP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    void rejectsAnonymousAndInvalidPageRequests() throws Exception {
        mvc.perform(delete("/users/me/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/me/notifications/page").header("Authorization", auth()).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/users/me/notifications/page").header("Authorization", auth()).param("category", "INVALID")).andExpect(status().isBadRequest());
    }
}
