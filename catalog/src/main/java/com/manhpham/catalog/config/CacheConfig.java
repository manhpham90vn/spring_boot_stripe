package com.manhpham.catalog.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/**
 * Bật cache cho hot path đọc của Catalog (đọc nhiều ghi ít). Backend là Redis
 * (xem {@code spring.cache.type=redis}). Giá trị serialize bằng JDK mặc định nên
 * các DTO response đều {@code implements Serializable}; có thể đổi sang JSON sau.
 * TTL ngắn để dữ liệu danh mục tự làm mới kể cả khi quên evict; write path đã
 * {@code @CacheEvict} chủ động.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheTtlCustomizer() {
        return builder -> builder.cacheDefaults(
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))
                        .disableCachingNullValues());
    }
}
