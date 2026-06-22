# Load test (k6)

## Chạy

```bash
cd load-tests

# Sanity – mọi endpoint còn sống (1 VU, 1 vòng)
k6 run smoke.js

# Hot-path đọc Catalog (không auth, không captcha)
k6 run catalog-browse.js

# Đăng ký + đăng nhập (bcrypt nặng CPU)
k6 run auth.js

# Van waiting room: enqueue + poll status (cần Redis)
k6 run waiting-room.js

# Flash sale tổng hợp: lướt + vào hàng song song (cần Redis)
k6 run -e SPOOF_IP=1 flash-sale.js
```
