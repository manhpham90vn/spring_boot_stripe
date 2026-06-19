package com.manhpham.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway — cửa vào DUY NHẤT cho traffic nghiệp vụ.
 *
 * <p>Chạy WebFlux/Netty thuần: TUYỆT ĐỐI KHÔNG thêm JPA/JDBC/DB blocking vì sẽ chẹn
 * event-loop lúc flash sale. Nhiệm vụ gói gọn ở biên: verify JWT (SecurityConfig),
 * rate limit + circuit breaker (GatewayConfig), định tuyến tới service qua K8s DNS
 * (application.properties). KHÔNG chứa business logic.
 */
@SpringBootApplication
public class ApigatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApigatewayApplication.class, args);
	}

}
