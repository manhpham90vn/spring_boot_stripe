package com.manhpham.auth.dto;

/**
 * Phản hồi trả về sau khi đăng nhập thành công, theo quy ước OAuth2:
 * <ul>
 *   <li>{@code accessToken} — chuỗi JWT để client đính kèm vào header
 *       {@code Authorization: Bearer <token>} ở các request sau.</li>
 *   <li>{@code tokenType} — luôn là "Bearer".</li>
 *   <li>{@code expiresIn} — số giây token còn sống, để client chủ động xin token mới.</li>
 * </ul>
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {

    /** Factory cho loại token "Bearer" (loại phổ biến nhất). */
    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
