-- =============================================================================
-- Database-per-service (CLAUDE.md: mỗi service sở hữu DB riêng, KHÔNG dùng chung).
-- Local dev: gom vào MỘT instance Postgres cho nhẹ, nhưng TÁCH database hẳn —
-- mỗi service chỉ trỏ tới database của mình, không query chéo.
-- Khi lên K8s (prod) sẽ tách thành cụm PostgreSQL riêng từng service.
--
-- notification: stateless → không có DB.
-- apigateway / waitingroom: không dùng Postgres (gateway không DB, waitingroom Redis).
-- "order" là từ khoá SQL nên phải đặt trong nháy kép.
-- =============================================================================

CREATE DATABASE auth;
CREATE DATABASE catalog;
CREATE DATABASE inventory;
CREATE DATABASE "order";
CREATE DATABASE payment;
CREATE DATABASE ticket;
