package com.example.service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/**
 * Sliding window counter strategy for rate limiting.
 * <p>There are two approaches to implement sliding window count rate limiting:
 * 1. By creating smaller buckets with single timestamp for each bucket and then checking the count
 * of requests in the bucket. This approach is slight better than sliding window log approach in
 * terms of memory usage where we store timestamp of each request.
 * <p>
 * 2. Using weighted counter of previous and current window. We are using this approach here as it
 * is more efficient in terms of memory usage.
 * <p>This strategy uses a sliding window counter to track the number of requests within a given
 * time period. The sliding window is divided into fixed windows of equal length. The current fixed
 * window is the current time period, and the previous fixed window is the previous time period. The
 * strategy calculates the weight of the previous window based on the overlap with the sliding
 * window. The total request count within the sliding window is calculated based on the weight of
 * the previous window and the count of the current window. If the request count within the sliding
 * window exceeds the limit, the request is rejected; otherwise, the request count is updated in the
 * current fixed window, and the request is allowed. This strategy is efficient for a large number
 * of requests and provides a uniform distribution of requests over the previous and current
 * windows. The sliding window count strategy is suitable for scenarios where the request rate is
 * expected to be uniform over time.
 * <p>
 * The SlidingWindowCountStrategy class implements the RateLimiterStrategy. The class uses a
 * ConcurrentHashMap to store the sliding window state for each user. The SlidingWindowCountStrategy
 * class is thread-safe and ensures atomicity of operations on the sliding window using a
 * synchronized block.
 */
@Service
public class RateLimiterService {

  private static final int ZERO = 0;

//  private final int maxAllowedRequestsPerPeriod;
//  private final int timePeriodInSeconds;
//  private final Clock clock;
  // ConcurrentHashMap to store the sliding window state for each user.
  private final ConcurrentMap<String, SlidingWindow> userSlidingWindow = new ConcurrentHashMap<>();

//  // Passing the timePeriodInSeconds, maxAllowedRequestsPerPeriod in the constructor so no need to
//  // pass with each method call.
//  public RateLimiterService(int timePeriodInSeconds, int maxAllowedRequestsPerPeriod,
//      Clock clock) {
//    this.timePeriodInSeconds = timePeriodInSeconds;
//    this.maxAllowedRequestsPerPeriod = maxAllowedRequestsPerPeriod;
//    this.clock = clock;
//  }

  public boolean isAllowed(String key, int timePeriodInSeconds, int maxAllowedRequestsPerPeriod, Clock clock) {
    long now = clock.millis();
    long windowLengthInMilliSeconds = timePeriodInSeconds * 1000L;

    // Initialize an empty sliding window for new users or retrieve existing one.
    SlidingWindow slidingWindow = userSlidingWindow.computeIfAbsent(key,
        k -> new SlidingWindow(new FixedWindow(now, ZERO),
            new FixedWindow(now, ZERO)));

    // Use synchronized block to ensure atomicity of operations on the sliding window.
    synchronized (slidingWindow) {
      FixedWindow currentFixedWindow = slidingWindow.currentFixedWindow();
      FixedWindow previousFixedWindow = slidingWindow.previousFixedWindow();

      // Transition to a new fixed window when the current one expires.
      if (currentFixedWindow.timestamp() + windowLengthInMilliSeconds < now) {
        previousFixedWindow = currentFixedWindow;
        currentFixedWindow = new FixedWindow(now, ZERO);
        userSlidingWindow.put(key,
            new SlidingWindow(previousFixedWindow, currentFixedWindow));
      }

      // Weight calculation for the previous window.
      long slidingWindowStart = Math.max(0, now - windowLengthInMilliSeconds);
      long previousFixedWindowEnd =
          previousFixedWindow.timestamp() + windowLengthInMilliSeconds;

      // Weight of the previous window based on overlap with the sliding window.
      // Math.max is necessary for cases when we don't have any new request for longer time,
      // and the previous window is completely outside the current sliding window. In that case,
      // the previousFixedWindowWeight will be negative, and we need to set it to 0.
      double previousFixedWindowWeight =
          Math.max(0, previousFixedWindowEnd - slidingWindowStart)
              / (double) windowLengthInMilliSeconds;

      // Calculate total request count within the sliding window.
      // if we have a rolling window of 10 seconds and allow 5 requests,
      // then we assume approximately 1 request is allowed every 2 seconds.
      // This is the gist of the "sliding window count" algorithm where we
      // assume uniform distribution of requests over the previous/current window verses
      // the "sliding window log" algorithm where we need to keep track of each and every timestamp
      // of the requests in the previous window and that makes it less efficient in terms of memory
      // usage for a large number of requests.

      // Assume that the previous window is 10 seconds and the current window is 10 seconds.
      // And we allow 5 requests per 10 seconds window. Now, we are at 14th second, which is 4th second
      // of the current window. So, the previous window is from 4th second to 14th second.
      // So now when we calculate the weight of the previous window, it will be 6/10 = 0.6.
      // And weight of the current window will be 0.4.
      // So, the total count will be 0.6 * count of previous window + count of current window.
      int count = (int) (previousFixedWindow.count()
          * previousFixedWindowWeight
          + currentFixedWindow.count());

      // Check if the request count within the sliding window exceeds the limit.
      // If so, reject the request; otherwise, update the request count
      // in the current fixed window and allow the request.
      if (count >= maxAllowedRequestsPerPeriod) {
        return false;
      } else {
        // Create a new FixedWindow with updated count
        currentFixedWindow = new FixedWindow(currentFixedWindow.timestamp(),
            currentFixedWindow.count() + 1);
        userSlidingWindow.put(key,
            new SlidingWindow(previousFixedWindow, currentFixedWindow));
        return true;
      }
    }
  }

  private static class SlidingWindow {
    private final FixedWindow previousFixedWindow;
    private final FixedWindow currentFixedWindow;

    public SlidingWindow(FixedWindow previousFixedWindow, FixedWindow currentFixedWindow) {
      this.previousFixedWindow = previousFixedWindow;
      this.currentFixedWindow = currentFixedWindow;
    }

    public FixedWindow previousFixedWindow() {
      return previousFixedWindow;
    }

    public FixedWindow currentFixedWindow() {
      return currentFixedWindow;
    }
  }


  private static class FixedWindow {
    private final long timestamp;
    private final int count;

    public FixedWindow(long timestamp, int count) {
      this.timestamp = timestamp;
      this.count = count;
    }

    public long timestamp() {
      return timestamp;
    }

    public int count() {
      return count;
    }
  }
}