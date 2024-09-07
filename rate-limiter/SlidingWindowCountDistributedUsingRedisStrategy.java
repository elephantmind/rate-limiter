package org.example;

import java.time.Clock;
import redis.clients.jedis.Jedis;

public class SlidingWindowCountDistributedUsingRedisStrategy implements RateLimiterStrategy {

  private final int maxAllowedRequestsPerPeriod;
  private final int timePeriodInSeconds;
  private final Clock clock;
  private final Jedis jedis;

  public SlidingWindowCountDistributedUsingRedisStrategy(int timePeriodInSeconds,
      int maxAllowedRequestsPerPeriod, Clock clock, Jedis jedis) {
    this.timePeriodInSeconds = timePeriodInSeconds;
    this.maxAllowedRequestsPerPeriod = maxAllowedRequestsPerPeriod;
    this.clock = clock;
    this.jedis = jedis;
  }

  public boolean allowed(String key) {
    long now = clock.millis();
    long windowLengthInMilliSeconds = timePeriodInSeconds * 1000L;

    String previousFixedWindowKey = key + ":previous";
    String currentFixedWindowKey = key + ":current";

    // Initialize or retrieve the fixed windows from Redis.
    FixedWindow previousFixedWindow = retrieveFixedWindow(jedis, previousFixedWindowKey, now);
    FixedWindow currentFixedWindow = retrieveFixedWindow(jedis, currentFixedWindowKey, now);

    // Transition to a new fixed window when the current one expires.
    if (currentFixedWindow.timestamp() + windowLengthInMilliSeconds < now) {
      previousFixedWindow = currentFixedWindow;
      currentFixedWindow = new FixedWindow(now, 0);
      saveFixedWindow(jedis, previousFixedWindowKey, previousFixedWindow);
      saveFixedWindow(jedis, currentFixedWindowKey, currentFixedWindow);
    }

    long slidingWindowStart = Math.max(0, now - windowLengthInMilliSeconds);
    long previousFixedWindowEnd = previousFixedWindow.timestamp() + windowLengthInMilliSeconds;

    double previousFixedWindowWeight = Math.max(0, previousFixedWindowEnd - slidingWindowStart)
        / (double) windowLengthInMilliSeconds;

    int count = (int) (previousFixedWindow.count() * previousFixedWindowWeight
        + currentFixedWindow.count());

    // int countCeiling = (int) Math.ceil((previousFixedWindow.count() * previousFixedWindowWeight + currentFixedWindow.count()));

    if (count >= maxAllowedRequestsPerPeriod) {
      return false;
    } else {
      currentFixedWindow = new FixedWindow(currentFixedWindow.timestamp(),
          currentFixedWindow.count() + 1);
      saveFixedWindow(jedis, currentFixedWindowKey, currentFixedWindow);
      return true;
    }
  }

  private FixedWindow retrieveFixedWindow(Jedis jedis, String key, long now) {
    String timestampStr = jedis.hget(key, "timestamp");
    String countStr = jedis.hget(key, "count");
    long timestamp = timestampStr != null ? Long.parseLong(timestampStr) : now;
    int count = countStr != null ? Integer.parseInt(countStr) : 0;
    return new FixedWindow(timestamp, count);
  }

  private void saveFixedWindow(Jedis jedis, String key, FixedWindow fixedWindow) {
    jedis.hset(key, "timestamp", String.valueOf(fixedWindow.timestamp()));
    jedis.hset(key, "count", String.valueOf(fixedWindow.count()));
  }

  private record FixedWindow(long timestamp, int count) {

  }
}
