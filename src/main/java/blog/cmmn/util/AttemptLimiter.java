package blog.cmmn.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

// 같은 키(IP)로 windowMillis 안에 maxAttempts 번을 넘으면 막는다. 메모리에만 둔다(서버 1대).
// 확인과 증가를 compute 안에서 한 번에 해서, 동시에 몰려온 요청도 maxAttempts 번까지만 통과한다.
public class AttemptLimiter {

    private static final int MAX_KEYS = 10_000; // 키 개수 상한(IP 를 바꿔 가며 보내는 요청으로 메모리가 늘지 않게)

    private final int maxAttempts;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AttemptLimiter(int maxAttempts, long windowMillis) {
        this(maxAttempts, windowMillis, System::currentTimeMillis);
    }

    // 테스트용: 시계를 바꿔 끼운다
    AttemptLimiter(int maxAttempts, long windowMillis, LongSupplier clock) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    // 허용이면 true(1회를 센다), 막혔으면 false.
    // 키가 상한만큼 차 있고 지난 기록을 지워도 자리가 없으면 새 키는 false(막는 쪽으로 실패).
    public boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        if (!windows.containsKey(key) && windows.size() >= MAX_KEYS) {
            windows.values().removeIf(w -> w.start + windowMillis <= now);
            if (windows.size() >= MAX_KEYS) {
                return false;
            }
        }

        boolean[] allowed = {false};
        windows.compute(key, (k, w) -> {
            if (w == null || w.start + windowMillis <= now) {
                w = new Window(now); // 처음이거나 시간 창이 지남
            }
            if (w.count < maxAttempts) {
                w.count++;
                allowed[0] = true;
            }
            return w;
        });
        return allowed[0];
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
        }
    }
}
