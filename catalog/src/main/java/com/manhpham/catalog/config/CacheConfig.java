package com.manhpham.catalog.config;

import com.manhpham.catalog.dto.EventDetailResponse;
import com.manhpham.catalog.dto.EventSummaryResponse;
import com.manhpham.catalog.dto.VenueResponse;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * Bật cache cho hot path đọc của Catalog (đọc nhiều ghi ít). Backend Redis
 * (xem {@code spring.cache.type=redis}). TTL ngắn để dữ liệu tự làm mới kể cả khi
 * quên evict; write path đã {@code @CacheEvict} chủ động.
 *
 * <p><b>Vì sao serialize value bằng JSON CÓ KIỂU CỐ ĐỊNH theo từng cache?</b>
 * <ul>
 *   <li><b>Không JDK serialization:</b> nó gắn chặt với từng instance classloader →
 *       khi DevTools nạp lại DTO bằng RestartClassLoader mới, object cũ trong Redis cast
 *       sang class (cùng tên) của loader mới sẽ ném {@code VenueResponse cannot be cast
 *       to VenueResponse}. JSON resolve theo tên kiểu → độc lập classloader, sống sót
 *       qua reload.</li>
 *   <li><b>Không generic + default-typing (@class):</b> với collection ở GỐC, default
 *       typing dạng {@code As.PROPERTY} không gắn được type cho mảng gốc → ghi ra
 *       {@code [{"@class":...}]} nhưng đọc lại đòi type-id ở gốc → lỗi round-trip.</li>
 *   <li><b>Cách dùng ở đây — kiểu cố định/binding:</b> mỗi cache biết trước kiểu value
 *       (List&lt;VenueResponse&gt;, List&lt;EventSummaryResponse&gt;, EventDetailResponse),
 *       JSON KHÔNG cần {@code @class}, round-trip chắc chắn.</li>
 * </ul>
 * Nhờ vậy các DTO response KHÔNG còn cần {@code implements Serializable}.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration TTL = Duration.ofMinutes(5);

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheCustomizer() {
        // ObjectMapper riêng cho cache; findAndAddModules() nạp cả java.time (Instant).
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

        JavaType venueList = mapper.getTypeFactory().constructCollectionType(List.class, VenueResponse.class);
        JavaType eventList = mapper.getTypeFactory().constructCollectionType(List.class, EventSummaryResponse.class);
        JavaType eventDetail = mapper.constructType(EventDetailResponse.class);

        // Tên cache phải khớp @Cacheable trong EventServiceImpl / VenueServiceImpl.
        return builder -> builder
                .withCacheConfiguration("venues", typed(mapper, venueList))
                .withCacheConfiguration("events", typed(mapper, eventList))
                .withCacheConfiguration("event", typed(mapper, eventDetail));
    }

    private static RedisCacheConfiguration typed(ObjectMapper mapper, JavaType type) {
        RedisSerializer<Object> ser = new JacksonJsonRedisSerializer<>(mapper, type);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(TTL)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(ser));
    }
}
