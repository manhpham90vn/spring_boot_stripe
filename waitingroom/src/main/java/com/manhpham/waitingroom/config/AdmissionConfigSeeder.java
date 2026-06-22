package com.manhpham.waitingroom.config;

import com.manhpham.waitingroom.services.AdmissionConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seed {@code wr:config} từ properties lúc khởi động (HSETNX — chỉ field còn thiếu) để Redis trở
 * thành NGUỒN DUY NHẤT của cấu hình admission. Fire-and-forget: nếu Redis chưa sẵn sàng thì
 * {@code current()} sẽ tự seed ở lần đọc đầu tiên — không chặn startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdmissionConfigSeeder implements ApplicationRunner {

    private final AdmissionConfigService config;

    @Override
    public void run(ApplicationArguments args) {
        config.seedIfAbsent()
                .doOnError(e -> log.warn("Seed wr:config lúc khởi động chưa được ({}); sẽ tự seed khi đọc đầu tiên",
                        e.toString()))
                .onErrorComplete()
                .subscribe();
    }
}
