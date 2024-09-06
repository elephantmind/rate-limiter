package org.example;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlidingWindowCountStrategyTest {

  private static final String USER1 = "User1";
  private static final String USER2 = "User2";
  private static final String USER3 = "User3";
  private SlidingWindowCountStrategy rateLimiter;
  private Clock clock;

  @BeforeEach
  void setUp() {
    // Set up a fixed clock to control time in tests
    clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

    // start rate limiter with 5 requests per second
    rateLimiter = new SlidingWindowCountStrategy(1, 5, clock);
  }

  @Test
  void testAllowRequestAfterWindow() throws InterruptedException {
    // Allow 5 requests
    for (int i = 0; i < 5; i++) {
      assertTrue(rateLimiter.allowed(USER1));
    }

    // Simulate time passing beyond the window
    clock = Clock.offset(clock, java.time.Duration.ofMillis(1000));
    rateLimiter = new SlidingWindowCountStrategy(1, 5, clock);

    // After the window has passed, allow requests again
    assertTrue(rateLimiter.allowed(USER1));
  }

  @Test
  void testBoundaryConditions() {
    // Allow 5 requests
    for (int i = 0; i < 5; i++) {
      assertTrue(rateLimiter.allowed(USER2));
    }

    // Simulate a new window by moving the clock forward
    clock = Clock.offset(clock, java.time.Duration.ofMillis(1000));
    rateLimiter = new SlidingWindowCountStrategy(1, 5, clock);

    // Allow 5 requests in the new window
    for (int i = 0; i < 5; i++) {
      assertTrue(rateLimiter.allowed(USER2));
    }

    // The 6th request should be denied if it exceeds the limit
    assertFalse(rateLimiter.allowed(USER2));
  }

  @Test
  void testTrafficBurst() {
    Clock clock = mock(Clock.class);
    when(clock.millis()).thenReturn(0L, 999L, 1000L, 1001L, 1002L, 1999L, 2000L, 2500L, 3000L);

    rateLimiter = new SlidingWindowCountStrategy(1, 2, clock);

    // 0 ------------------ 1000 ----------------- 2000 ----------- 3000 -------------> time

    // 0-1 seconds (0 to 1000 milliseconds): 2 requests allowed

    assertTrue(rateLimiter.allowed(USER3),
        String.format("%s's request 1 at timestamp=0 should be allowed", USER3));

    // 0 -----------------------998 999 1000 ----------------------- 2000 ----> time
    // R1@0---------------------- R2@999 --------------------------> requests
    assertTrue(rateLimiter.allowed(USER3),
        String.format("%s's request 2 at timestamp=999 should be allowed", USER3));

    // Exactly at 1 second = 1000 milliseconds
    // 0 -----------------------998 999 1000 ----------------------- 2000 ----> time
    // R1@0---------------------- R2@999 R3@1000--------------------------> requests
    // 3 requests between 0 and 1000 milliseconds (1 second) window
    assertFalse(rateLimiter.allowed(USER3),
        String.format("%s's request 3 at timestamp=1000 should be blocked", USER3));

    // 1-2 seconds (1001 to 2000 milliseconds): 2 requests allowed

    // 0 -----------------------998 999 1000 1001 1002 ----------------------- 2000 ----> time
    // 1 ---------------------- R2@999 - R4@1001--------------------------> requests
    // sliding window is now 1 to 1001 milliseconds
    assertTrue(rateLimiter.allowed(USER3),
        String.format(
            "%s's request 4 at timestamp=1001 should be allowed because request 1 "
                + "at timestamp=0 is outside the current sliding window [1; 1001]", USER3));

    // 2 ---------------------- R2@999 - R4@1001 R5@1002--------------------------> requests
    // sliding window is now 2 to 1002 milliseconds
    assertFalse(rateLimiter.allowed(USER3),
        String.format("%s's request 5 at timestamp=1002 should be blocked", USER3));

    // sliding window is now 999 to 1999 milliseconds
    assertTrue(rateLimiter.allowed(USER3),
        String.format("%s's request 6 at timestamp=1999 should be allowed", USER3));

    // 2-3 seconds: 2 requests allowed
    assertFalse(rateLimiter.allowed(USER3),
        String.format("%s's request 7 at timestamp=2000 should be blocked", USER3));

    assertTrue(rateLimiter.allowed(USER3),
        String.format("%s's request 8 at timestamp=2500 should be allowed", USER3));

    assertTrue(rateLimiter.allowed(USER3),
        String.format("%s's request 8 at timestamp=3000 should be allowed", USER3));
  }
}
