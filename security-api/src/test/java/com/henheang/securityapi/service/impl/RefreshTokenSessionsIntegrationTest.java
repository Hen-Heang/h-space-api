package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.RefreshToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.RefreshTokenRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.service.RefreshTokenService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Each sign-in is its own session: a second sign-in (another device or browser) must not end the
 * first, and rotating one session must not touch another. Runs against the real schema and queries.
 * Not @Transactional - each service call commits, so users and tokens are deleted afterwards.
 */
@SpringBootTest
class RefreshTokenSessionsIntegrationTest {

    // Matches jwt.refresh-token.max-active-per-user's default.
    private static final int SESSION_CAP = 10;

    @Autowired private RefreshTokenService refreshTokens;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;

    private final List<User> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (User user : created) {
            refreshTokenRepository.deleteAll(refreshTokenRepository.findAllByUser(user));
            userRepository.deleteById(user.getId());
        }
    }

    private User user() {
        User user = new User();
        user.setEmail("sessions-" + UUID.randomUUID() + "@example.com");
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        user = userRepository.saveAndFlush(user);
        created.add(user);
        return user;
    }

    private boolean valid(RefreshToken token) {
        return refreshTokens.validateRefreshToken(token.getRawToken()).isPresent();
    }

    @Test
    void aSecondSignIn_leavesTheFirstSessionValid() {
        User user = user();

        RefreshToken laptop = refreshTokens.createRefreshToken(user);
        RefreshToken phone = refreshTokens.createRefreshToken(user);

        assertThat(valid(laptop)).isTrue();
        assertThat(valid(phone)).isTrue();
    }

    @Test
    void rotatingOneSession_spendsOnlyThatTokenAndLeavesTheOtherSession() {
        User user = user();
        RefreshToken laptop = refreshTokens.createRefreshToken(user);
        RefreshToken phone = refreshTokens.createRefreshToken(user);

        RefreshToken rotated = refreshTokens.rotateRefreshToken(laptop.getRawToken()).orElseThrow();

        assertThat(valid(laptop)).isFalse();
        assertThat(valid(rotated)).isTrue();
        assertThat(valid(phone)).isTrue();
    }

    @Test
    void aSpentTokenCannotBeRotatedAgain() {
        User user = user();
        RefreshToken laptop = refreshTokens.createRefreshToken(user);
        refreshTokens.rotateRefreshToken(laptop.getRawToken()).orElseThrow();

        assertThat(refreshTokens.rotateRefreshToken(laptop.getRawToken())).isEmpty();
    }

    @Test
    void overTheSessionCap_onlyTheOldestSessionIsDropped() {
        User user = user();
        List<RefreshToken> sessions = new ArrayList<>();
        for (int i = 0; i < SESSION_CAP + 1; i++) {
            sessions.add(refreshTokens.createRefreshToken(user));
        }

        assertThat(valid(sessions.get(0))).isFalse();
        for (RefreshToken session : sessions.subList(1, sessions.size())) {
            assertThat(valid(session)).isTrue();
        }
    }

    @Test
    void rotatingAtTheSessionCap_doesNotEvictAnotherSession() {
        User user = user();
        List<RefreshToken> sessions = new ArrayList<>();
        for (int i = 0; i < SESSION_CAP; i++) {
            sessions.add(refreshTokens.createRefreshToken(user));
        }

        RefreshToken rotated =
                refreshTokens.rotateRefreshToken(sessions.get(5).getRawToken()).orElseThrow();

        assertThat(valid(rotated)).isTrue();
        for (int i = 0; i < SESSION_CAP; i++) {
            if (i != 5) {
                assertThat(valid(sessions.get(i))).isTrue();
            }
        }
    }

    @Test
    void revokeAllUserTokens_endsEverySession() {
        User user = user();
        RefreshToken laptop = refreshTokens.createRefreshToken(user);
        RefreshToken phone = refreshTokens.createRefreshToken(user);

        refreshTokens.revokeAllUserTokens(user);

        assertThat(valid(laptop)).isFalse();
        assertThat(valid(phone)).isFalse();
    }
}
