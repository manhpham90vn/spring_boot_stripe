package com.manhpham.waitingroom.dto;

/**
 * Trạng thái khi poll. Ba khả năng:
 * <ul>
 *   <li>còn chờ: {@code admitted=false}, {@code position>0}.</li>
 *   <li>tới lượt: {@code admitted=true}, {@code accessToken} = PASS để hạ nguồn ({@code /api/order})
 *       cho phép đặt mua; {@code position=0}.</li>
 *   <li>hết vé/hết hạn: {@code soldOut=true} hoặc không còn trong hàng — client dừng.</li>
 * </ul>
 */
public record StatusResponse(long position, boolean admitted, String accessToken, boolean soldOut) {

    public static StatusResponse waiting(long position) {
        return new StatusResponse(position, false, null, false);
    }

    public static StatusResponse admitted(String accessToken) {
        return new StatusResponse(0, true, accessToken, false);
    }

    public static StatusResponse soldOutResponse() {
        return new StatusResponse(0, false, null, true);
    }
}
