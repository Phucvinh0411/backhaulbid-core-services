INSERT INTO admin_settings (scope, settings_json)
VALUES
    ('general', '{"platformName":"BackHaulBid","systemEmail":"noreply@backhaulbid.vn","supportPhone":"1900 8899","timezone":"GMT+7","maintenanceMode":false}'),
    ('auction', '{"defaultAuctionTime":30,"minBidIncrement":"50000","autoExtend":true,"extendDuration":5,"paymentDeadline":24}'),
    ('payment', '{"commissionRate":5,"minDepositShipper":"1000000","minDepositCarrier":"500000","paymentGateway":"vnpay","maxWithdrawLimit":"50000000"}'),
    ('notification', '{"smsNotifications":true,"emailNotifications":true,"pushNotifications":true}')
ON CONFLICT (scope) DO NOTHING;
