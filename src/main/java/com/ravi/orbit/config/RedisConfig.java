//package com.ravi.orbit.config;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.cache.Cache;
//import org.springframework.cache.annotation.CachingConfigurer;
//import org.springframework.cache.interceptor.CacheErrorHandler;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.cache.RedisCacheConfiguration;
//import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
//import org.springframework.data.redis.serializer.RedisSerializationContext;
//
//import java.time.Duration;
//
//@Slf4j
//@Configuration
//public class RedisConfig implements CachingConfigurer {
//
//    @Bean
//    public RedisCacheConfiguration cacheConfiguration() {
//
//        return RedisCacheConfiguration.defaultCacheConfig()
//                .entryTtl(Duration.ofMinutes(30))
//                .disableCachingNullValues()
//                .serializeValuesWith(
//                        RedisSerializationContext.SerializationPair
//                                .fromSerializer(
//                                        new GenericJackson2JsonRedisSerializer()
//                                )
//                );
//    }
//
//    @Bean
//    public CacheErrorHandler cacheErrorHandler() {
//
//        return new CacheErrorHandler() {
//
//            @Override
//            public void handleCacheGetError(
//                    RuntimeException exception,
//                    Cache cache,
//                    Object key) {
//
//                log.warn(
//                        "Redis cache GET failed. cache={}, key={}. " +
//                                "Falling back to database.",
//                        cache.getName(),
//                        key,
//                        exception
//                );
//            }
//
//            @Override
//            public void handleCachePutError(
//                    RuntimeException exception,
//                    Cache cache,
//                    Object key,
//                    Object value) {
//
//                log.warn(
//                        "Redis cache PUT failed. cache={}, key={}. " +
//                                "Continuing without caching.",
//                        cache.getName(),
//                        key,
//                        exception
//                );
//            }
//
//            @Override
//            public void handleCacheEvictError(
//                    RuntimeException exception,
//                    Cache cache,
//                    Object key) {
//
//                log.warn(
//                        "Redis cache EVICT failed. cache={}, key={}.",
//                        cache.getName(),
//                        key,
//                        exception
//                );
//            }
//
//            @Override
//            public void handleCacheClearError(
//                    RuntimeException exception,
//                    Cache cache) {
//
//                log.warn(
//                        "Redis cache CLEAR failed. cache={}.",
//                        cache.getName(),
//                        exception
//                );
//            }
//        };
//    }
//
//    @Override
//    public CacheErrorHandler errorHandler() {
//        return cacheErrorHandler();
//    }
//
//}
