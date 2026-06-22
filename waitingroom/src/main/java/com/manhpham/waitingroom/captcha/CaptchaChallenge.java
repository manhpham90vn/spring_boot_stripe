package com.manhpham.waitingroom.captcha;

/**
 * Một thử thách CAPTCHA: {@code captchaId} để client gửi kèm khi enqueue, {@code png} là ảnh chứa
 * con số ngẫu nhiên cần nhập lại. Đáp án KHÔNG trả về client — lưu ở Redis ({@code wr:captcha:{id}}).
 */
public record CaptchaChallenge(String captchaId, byte[] png) {
}
