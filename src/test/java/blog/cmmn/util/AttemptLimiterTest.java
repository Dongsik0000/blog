package blog.cmmn.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttemptLimiterTest {

    // 시간 창 안에서는 maxAttempts 번까지만, 창이 지나면 다시 허용
    @Test
    void blocksAfterMaxAttemptsUntilWindowPasses() {
        long[] now = {0};
        AttemptLimiter limiter = new AttemptLimiter(2, 1000, () -> now[0]);

        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("2.2.2.2")); // 다른 IP 는 따로 센다

        now[0] = 1000;
        assertTrue(limiter.tryAcquire("1.1.1.1"));
    }
}
