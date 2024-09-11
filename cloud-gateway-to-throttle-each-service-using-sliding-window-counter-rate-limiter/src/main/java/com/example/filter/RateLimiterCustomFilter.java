package com.example.filter;

import com.example.service.RateLimiterService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RateLimiterCustomFilter extends AbstractGatewayFilterFactory<RateLimiterCustomFilter.Config> {

  Logger logger = LoggerFactory.getLogger(RateLimiterCustomFilter.class);

  @Autowired
  private final RateLimiterService rateLimiterService;


  public RateLimiterCustomFilter(RateLimiterService rateLimiterService) {
    super(Config.class);
    this.rateLimiterService = rateLimiterService;
  }

  @Override
  public GatewayFilter apply(Config config) {
    //Custom Pre Filter. Suppose we can extract JWT and perform Authentication
    return (exchange, chain) -> {
      logger.info("First pre filter" + exchange.getRequest());

      // Access properties from config
      int maxAllowedRequestsPerPeriod = config.getMaxAllowedRequestsPerPeriod();
      int timePeriodInSeconds = config.getTimePeriodInSeconds();

      // Your rate limiting logic using maxRequests and timeWindowInSeconds
      if (!rateLimiterService.isAllowed(getKey(exchange),timePeriodInSeconds, maxAllowedRequestsPerPeriod, Clock.systemUTC())) {
//        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
//        return exchange.getResponse().setComplete();
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

        // Set retry headers
        HttpHeaders headers = response.getHeaders();
        headers.add("X-Retry-After", String.valueOf(timePeriodInSeconds/maxAllowedRequestsPerPeriod)); // Retry after 60 seconds
        headers.add("X-RateLimit-Limit", String.valueOf(maxAllowedRequestsPerPeriod));
        headers.add("X-RateLimit-Remaining", "0");

        return response.setComplete();
      }

      //Custom Post Filter.Suppose we can call error response handler based on error code.
      return chain.filter(exchange).then(Mono.fromRunnable(() -> {
        logger.info("First post filter");
      }));
    };
  }

//  @Override
//  public GatewayFilter apply(Config config) {
//    return (exchange, chain) -> {
//
//      if (true) {
//        return onError(exchange, "Rate limit exceeded");
//      }
//      return chain.filter(exchange);
//    };
//  }

  private Mono<Void> onError(ServerWebExchange exchange, String message) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    DataBuffer buffer = response.bufferFactory().wrap(message.getBytes());
    return response.writeWith(Mono.just(buffer));
  }

  private String getKey(ServerWebExchange exchange) {
    // Example of generating a key based on client IP
    return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
  }


  public static class Config {
    // Example properties for the rate limiter filter
    private int maxAllowedRequestsPerPeriod;
    private int timePeriodInSeconds;

    // Getters and setters for the properties

    public int getMaxAllowedRequestsPerPeriod() {
      return maxAllowedRequestsPerPeriod;
    }

    public void setMaxAllowedRequestsPerPeriod(int maxAllowedRequestsPerPeriod) {
      this.maxAllowedRequestsPerPeriod = maxAllowedRequestsPerPeriod;
    }

    public int getTimePeriodInSeconds() {
      return timePeriodInSeconds;
    }

    public void setTimePeriodInSeconds(int timePeriodInSeconds) {
      this.timePeriodInSeconds = timePeriodInSeconds;
    }
  }
}
