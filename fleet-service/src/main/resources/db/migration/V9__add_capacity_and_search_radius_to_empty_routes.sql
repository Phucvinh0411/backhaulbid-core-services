-- V9: Bổ sung tải trọng còn trống, loại xe và bán kính tìm kiếm cho tuyến xe rỗng
ALTER TABLE empty_routes
    ADD COLUMN IF NOT EXISTS available_capacity DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS truck_type        VARCHAR(60),
    ADD COLUMN IF NOT EXISTS search_radius     INTEGER DEFAULT 50;

COMMENT ON COLUMN empty_routes.available_capacity IS 'Tải trọng còn trống (tấn) – do tài xế/chủ xe khai báo';
COMMENT ON COLUMN empty_routes.truck_type        IS 'Loại phương tiện: TRUCK_VAN, TRUCK_BOX, CONTAINER_TRACTOR...';
COMMENT ON COLUMN empty_routes.search_radius     IS 'Bán kính tìm kiếm lô hàng xung quanh điểm xuất phát (km)';
