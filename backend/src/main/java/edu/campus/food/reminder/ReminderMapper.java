package edu.campus.food.reminder;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReminderMapper {
    @Select("SELECT id,name,license_expires_on FROM supplier WHERE active=TRUE AND license_expires_on IS NOT NULL AND license_expires_on<=DATE_ADD(CURRENT_DATE(), INTERVAL 30 DAY) ORDER BY license_expires_on LIMIT 100")
    List<Map<String, Object>> suppliers();

    @Select("SELECT b.id,b.canteen_id,b.batch_code,b.expires_on FROM purchase_batch b WHERE b.status='ACCEPTED' AND b.available_quantity>0 AND b.expires_on IS NOT NULL AND b.expires_on<=DATE_ADD(CURRENT_DATE(), INTERVAL 7 DAY) ORDER BY b.expires_on LIMIT 100")
    List<Map<String, Object>> batches();

    @Select("SELECT id,canteen_id,assigned_to,description,due_at FROM issue WHERE status<>'CLOSED' AND due_at<NOW() ORDER BY due_at LIMIT 100")
    List<Map<String, Object>> overdueIssues();

    @Select("SELECT id,canteen_id,inspector_id,scheduled_at FROM inspection WHERE status='SCHEDULED' AND scheduled_at BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 3 DAY) ORDER BY scheduled_at LIMIT 100")
    List<Map<String, Object>> inspections();

    @Select("""
            SELECT p.user_id,p.canteen_id,COALESCE(p.real_name,u.display_name) AS display_name,p.health_expires_on
            FROM staff_profile p JOIN app_user u ON u.id=p.user_id
            WHERE p.approved_submission_id IS NOT NULL AND p.health_expires_on IS NOT NULL
            AND p.health_expires_on<=DATE_ADD(CURRENT_DATE(), INTERVAL 30 DAY)
            AND EXISTS(SELECT 1 FROM user_role r WHERE r.user_id=p.user_id AND r.role_name IN ('STAFF','PURCHASER','CHEF','CLEANER'))
            AND EXISTS(SELECT 1 FROM user_canteen c WHERE c.user_id=p.user_id AND c.canteen_id=p.canteen_id)
            ORDER BY p.health_expires_on
            """)
    List<Map<String, Object>> healthCertificates();

    @Select("""
            SELECT s.id,s.user_id,s.canteen_id,s.real_name,s.status,s.submitted_at,s.reviewed_at
            FROM staff_profile_submission s
            WHERE s.status IN ('PENDING','APPROVED','REJECTED')
            AND NOT EXISTS(SELECT 1 FROM staff_profile_submission newer
                WHERE newer.user_id=s.user_id AND newer.canteen_id=s.canteen_id AND newer.id>s.id)
            AND EXISTS(SELECT 1 FROM user_role r WHERE r.user_id=s.user_id AND r.role_name IN ('STAFF','PURCHASER','CHEF','CLEANER'))
            AND EXISTS(SELECT 1 FROM user_canteen c WHERE c.user_id=s.user_id AND c.canteen_id=s.canteen_id)
            ORDER BY s.id DESC
            """)
    List<Map<String, Object>> healthSubmissions();

    @Select("SELECT id,canteen_id,user_id,assigned_to,investigation_note,status FROM feedback WHERE status IN ('NEW','PROCESSING','RESPONDED') ORDER BY id DESC LIMIT 100")
    List<Map<String, Object>> feedbacks();

    @Select("SELECT event_key FROM reminder_read WHERE user_id=#{userId}")
    List<String> readKeys(long userId);

    @Insert("INSERT IGNORE INTO reminder_read(user_id,event_key) VALUES(#{userId},#{eventKey})")
    int markRead(@Param("userId") long userId, @Param("eventKey") String eventKey);
}
