package com.manhpham.waitingroom.captcha;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * CAPTCHA ảnh cho dev: sinh con số ngẫu nhiên 5 chữ số, vẽ lên PNG có nhiễu, lưu đáp án vào Redis
 * ({@code wr:captcha:{id}}, TTL ngắn). Client nhập lại số → {@link #verify} đối chiếu rồi XOÁ key
 * (one-time, chống dùng lại). Đủ chặn bot ngây thơ ở local mà không phụ thuộc dịch vụ ngoài;
 * prod thay bằng reCAPTCHA/Turnstile.
 *
 * <p>Render ảnh ({@code java.awt}) là CPU blocking → đẩy sang {@link Schedulers#boundedElastic()}
 * để KHÔNG chẹn event-loop Netty.
 */
@Component
@Slf4j
public class ImageCaptchaVerifier implements CaptchaVerifier {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TTL = Duration.ofMinutes(3);

    private final ReactiveStringRedisTemplate redis;

    public ImageCaptchaVerifier(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(String captchaId) {
        return "wr:captcha:" + captchaId;
    }

    @Override
    public Mono<CaptchaChallenge> issue() {
        String captchaId = UUID.randomUUID().toString();
        String answer = String.format("%05d", RANDOM.nextInt(100_000)); // 00000–99999
        return Mono.fromCallable(() -> render(answer))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(png -> redis.opsForValue().set(key(captchaId), answer, TTL)
                        .thenReturn(new CaptchaChallenge(captchaId, png)));
    }

    @Override
    public Mono<Boolean> verify(String captchaId, String answer) {
        if (captchaId == null || captchaId.isBlank() || answer == null) {
            return Mono.just(false);
        }
        String redisKey = key(captchaId);
        return redis.opsForValue().get(redisKey)
                // XOÁ trước khi so: dùng 1 lần dù đúng hay sai (chống brute-force đáp án).
                .flatMap(expected -> redis.delete(redisKey).thenReturn(expected.equals(answer.trim())))
                .defaultIfEmpty(false);
    }

    /** Vẽ con số lên ảnh PNG kèm chút nhiễu (đường kẻ + lệch chữ). */
    private byte[] render(String text) {
        int w = 160, h = 60;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        // nhiễu: vài đường kẻ ngẫu nhiên
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(RANDOM.nextInt(200) + 30, RANDOM.nextInt(200) + 30, RANDOM.nextInt(200) + 30));
            g.drawLine(RANDOM.nextInt(w), RANDOM.nextInt(h), RANDOM.nextInt(w), RANDOM.nextInt(h));
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        int x = 18;
        for (char c : text.toCharArray()) {
            g.setColor(new Color(RANDOM.nextInt(120), RANDOM.nextInt(120), RANDOM.nextInt(120)));
            int y = 42 + RANDOM.nextInt(8) - 4; // lệch dọc nhẹ
            g.drawString(String.valueOf(c), x, y);
            x += 26;
        }
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không render được CAPTCHA", e);
        }
    }
}
