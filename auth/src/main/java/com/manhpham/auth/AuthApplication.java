package com.manhpham.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auth/User service — đăng ký, đăng nhập, PHÁT JWT và công bố JWKS để cả hệ thống
 * verify token. Sở hữu user/credential/role (DB Postgres riêng). Phát event qua outbox.
 */
@SpringBootApplication
@EnableScheduling // bật @Scheduled cho job dọn rác outbox (OutboxPurgeJob)
public class AuthApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthApplication.class, args);
	}

}
