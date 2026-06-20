package com.manhpham.notification.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Bản SAO phía nhận của event UserRegisteredEvent (gốc nằm ở Auth service). Cố ý KHÔNG
 * dùng chung class với Auth: mỗi service tự sở hữu model của mình (database/model-per-
 * service), giao tiếp với nhau qua "hợp đồng trên đường truyền" (wire contract) là JSON.
 *
 * <p>Vì Jackson khớp theo TÊN FIELD khi deserialize JSON, các tên ở đây ({@code userId},
 * {@code email}, {@code occurredAt}) PHẢI trùng với bên gửi. Chỉ cần khai những field mình
 * dùng; field thừa trong JSON sẽ được bỏ qua.
 */
public record UserRegisteredEvent(UUID userId, String email, Instant occurredAt) {
}
