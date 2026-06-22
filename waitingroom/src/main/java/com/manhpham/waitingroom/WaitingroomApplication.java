package com.manhpham.waitingroom;

import com.manhpham.waitingroom.config.WaitingRoomProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // bộ thả admission (AdmissionJob) chạy định kỳ
@EnableConfigurationProperties(WaitingRoomProperties.class)
public class WaitingroomApplication {

	public static void main(String[] args) {
		SpringApplication.run(WaitingroomApplication.class, args);
	}

}
