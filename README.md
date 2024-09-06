Rate Limiter - Sliding Window Counter

## Problem
Implement a rate limiter which can be used to limit the number of requests per time period. 
The rate limiter should be able to handle a large number of requests (millions) and should be able to handle race conditions.

We are focusing more on the algorithmic aspect of the problem rather than the high level design of the Rate Limiter.
 

## High Level Design of Rate Limiter

``` 
Rate Limiter can be integrated at different levels of the system:
1. Inside Edge Servers
2. Inside Load Balancers
3. Inside API Gateway
4. As a standalone service
5. As a sidecar in service mesh
6. Inside the application code
7. At the client side
```

We are trying to implement algorithm of rate limiter which can be used at any of the above levels.
There are standard libraries available for rate limiting like Guava, Google's Token Bucket, 
Spring's Resilience4j, Spring Cloud Gateway, etc. We are trying to implement our own custom rate 
limiter which is based on suggestions provided in the following articles:

https://www.figma.com/blog/an-alternative-approach-to-rate-limiting/
https://blog.cloudflare.com/counting-things-a-lot-of-different-things/


## Solution
There are five techniques to implement rate limiting:
1. Fixed Window Counter
2. Sliding Window Log
3. Sliding Window Counter
4. Token Bucket
5. Leaky Bucket

In this implementation, we will use the Sliding Window Counter technique.

### Sliding Window Counter
There are two approaches to implement sliding window counter. Both of these approaches are hybrid 
version of "Fixed Window" algorithm and "Sliding Window Log" algorithm. They are more efficient 
than sliding window log and more capable of handling traffic bursts than fixed window counter.

Approach #1:

Let's take an example: Say we are building a rate limiter of 100 requests/hour. 
Say a bucket size of 20 minutes is chosen, then there are 3 buckets in the unit time.

For a window time of 2AM to 3AM, the buckets are
```
{
"2AM-2:20AM": 10,
"2:20AM-2:40AM": 20,
"2:40AM-3:00AM": 30
}
```

If a request is received at 2:50AM, we find out the total requests in last 3 buckets including the 
current and add them, in this case they sum up to 60 (<100), so a new request is added to the 
bucket of 2:40AM – 3:00AM giving below:
```
{
"2AM-2:20AM": 10,
"2:20AM-2:40AM": 20,
"2:40AM-3:00AM": 31
}
```
This method stores the count of requests in each bucket and the timestamp of the last request. 
Please pay a close attention here in terms of the number of buckets/timestamps getting stored. 
It is the main difference between this approach and the sliding window log approach. 
In sliding window log, it would have stored 60 timestamps for 60 requestes at different times but 
here it distributes those requests into buckets of smaller windows within a larger window, so we 
reduce the footprint of number of timestamps stored.

Back of the envelope calculation for Approach #1:
```
```


Approach 2:
In the sliding window counter algorithm, instead of fixed window size, we have a rolling window of 
time to smooth bursts. The windows are typically defined by the floor of the current timestamp, 
so 12:03:15 with a 60-second window length would be in the 12:03:00 window.

Let’s say we want to limit 100 requests per hour on an API and assume there are 84 requests in the 
time window [12:00–1:00] and 36 requests current window [1:00 to 2:00] which started 15 minutes ago.
Now imagine a new request arrives at 1:15. To decide, whether we should accept this request or deny 
it will be based on the approximation.

The approximation rate will be calculated like this:
```
limit = 100 requests/hour

    rate = ( 84 * ((time interval between 1:00 and 12:15) / rolling window size) ) + 36
    = 84 * ((60 - 15)/60) + 36
    = 84 * 0.75 + 36
    = 99

    rate < 100
    hence, we will accept this request.
```
Since the requests in the current window [12:15 – 1:15] are 99 which is less than our limit of 100 
requests/hour, hence this request will be accepted. But any new request during the next second will 
not be accepted.

We can explain the above calculation in a more lucid way: Assume the rate limiter allows a maximum of 100 requests per hour, and there are 84 requests in the previous hour and 36 requests in the current hour. For a new request that arrives at a 25% position in the current hour, the number of requests in the rolling window is calculated using the following formula:

```Requests in current window + (Requests in the previous window * overlap percentage of the rolling window and previous window)
```
Using the above formula, we get (36 + (84 * 75%)) = 99. Since the rate limiter allows 100 requests 
per hour, the current request will go through.

This algorithm assumes a constant request rate in the (any) previous window, hence the result is 
only an approximated value but it is good enough for most practical purposes. 
For example at Cloudflare, they had inaccuracy of just 0.003% based on their analysis on 400 million
requests from 270,000 distinct sources.

Pros:
1. It is more efficient than sliding window log in terms of memory footprint and speed.
2. It is more capable of handling traffic bursts than fixed window counter.

Cons:
1. It is not as accurate as sliding window log.
2. It is not as simple as fixed window counter.


Back of the envelope calculation for Approach #2:
```
```

### Distributed Rate Limiter
In a distributed rate limiter, we can use the sliding window counter algorithm with a central 
storage like Redis or any other distributed cache so that all the instances of the rate limiter can
access the counter and make decision based on the total requests consumed by any User/IP/ApiKey etc.
across all the instances across the globe.

### how to use:

Git clone the repository and run the following command:
```
mvn clean install
```
And run the following command to run the test cases:
```
mvn test
```

If you are using any IDE, you can run the test cases by running the test class RateLimiterTest.java
Also, you can run the main class RateLimiterMain.java to see the output of the rate limiter.

### References:
1. https://www.figma.com/blog/an-alternative-approach-to-rate-limiting/
2. https://blog.cloudflare.com/counting-things-a-lot-of-different-things/
3. https://konghq.com/blog/how-to-design-a-scalable-rate-limiting-algorithm/
4. https://www.nginx.com/blog/rate-limiting-nginx/
5. https://arpitbhayani.me/blogs/sliding-window-ratelimiter/
