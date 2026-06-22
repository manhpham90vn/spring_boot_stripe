package com.manhpham.waitingroom.captcha;

import reactor.core.publisher.Mono;

/**
 * CAPTCHA chống bot trước khi xếp hàng (chỗ trong hàng = cơ hội mua). Tách interface để thay nhà
 * cung cấp thật (reCAPTCHA/hCaptcha/Turnstile) mà không đụng service.
 *
 * <p>Dev dùng {@link ImageCaptchaVerifier}: sinh ảnh chứa con số ngẫu nhiên → client nhập lại →
 * verify đối chiếu đáp án lưu ở Redis (one-time, có TTL).
 */
public interface CaptchaVerifier {

    /** Sinh thử thách mới: ảnh số ngẫu nhiên + captchaId (đáp án lưu phía server). */
    Mono<CaptchaChallenge> issue();

    /** {@code true} nếu {@code answer} khớp đáp án của {@code captchaId} (và tiêu thụ nó). */
    Mono<Boolean> verify(String captchaId, String answer);
}
