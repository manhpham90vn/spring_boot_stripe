package com.manhpham.waitingroom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient reactive tới Inventory để đọc tồn kho còn lại (admission rate phải biết tồn kho).
 * KHÔNG JPA/JDBC — đúng ràng buộc service WebFlux thuần. Gọi thẳng DNS nội bộ {@code /internal/**}
 * (bỏ qua gateway), rào ở tầng mạng (NetworkPolicy).
 */
@Configuration
public class WebClientConfig {

    @Bean
    WebClient inventoryWebClient(@Value("${waitingroom.inventory.base-url:http://localhost:8083}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
