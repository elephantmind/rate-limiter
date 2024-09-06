package org.example;

/**
 * RateLimiterStrategy interface to implement rate limiting strategies.
 * <p> In the future, we can add more rate limiting strategies by implementing this interface.
 * This way existing code will not be affected, and we can add new strategies without changing the
 * existing code.
 */
public interface RateLimiterStrategy {

  boolean allowed(String userId);
}