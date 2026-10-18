INSERT IGNORE INTO user_role(user_id, role_name)
SELECT DISTINCT r.user_id, 'CHEF' FROM user_role r JOIN staff_profile p ON p.user_id = r.user_id
WHERE r.role_name = 'STAFF' AND p.position_name LIKE '%厨%';

INSERT IGNORE INTO user_role(user_id, role_name)
SELECT DISTINCT r.user_id, 'PURCHASER' FROM user_role r JOIN staff_profile p ON p.user_id = r.user_id
WHERE r.role_name = 'STAFF' AND p.position_name LIKE '%采购%';

INSERT IGNORE INTO user_role(user_id, role_name)
SELECT DISTINCT r.user_id, 'CLEANER' FROM user_role r JOIN staff_profile p ON p.user_id = r.user_id
WHERE r.role_name = 'STAFF' AND (p.position_name LIKE '%保洁%' OR p.position_name LIKE '%清洁%');

DELETE legacy FROM user_role legacy JOIN user_role duty ON duty.user_id = legacy.user_id
WHERE legacy.role_name = 'STAFF' AND duty.role_name IN ('CHEF', 'PURCHASER', 'CLEANER');
