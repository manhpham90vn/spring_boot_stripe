package com.manhpham.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // cho StockReconciliationJob chạy theo lịch
public class InventoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryApplication.class, args);
	}

}
