-- V8: Bổ sung tọa độ điểm đến và thời gian dự kiến đến cho tuyến xe rỗng
ALTER TABLE empty_routes
ADD COLUMN IF NOT EXISTS dest_latitude DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS dest_longitude DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS expected_arrival_time TIMESTAMP;

-- Cập nhật dữ liệu mẫu cho bản ghi đã seed
UPDATE empty_routes
SET dest_latitude = 21.0285,
    dest_longitude = 105.8542,
    expected_arrival_time = expected_empty_time + INTERVAL '1 day 12 hours'
WHERE id = '55555555-e001-0000-0000-000000000001' AND expected_arrival_time IS NULL;
