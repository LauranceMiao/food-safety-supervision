package edu.campus.food.personnel;

import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface PersonnelMapper {
    @Select("""
            SELECT p.*,u.display_name,u.username,c.name AS canteen_name,r.display_name AS reviewer_name
            FROM staff_profile p JOIN app_user u ON u.id=p.user_id JOIN canteen c ON c.id=p.canteen_id
            LEFT JOIN app_user r ON r.id=p.approved_by
            WHERE p.approved_submission_id IS NOT NULL
              AND EXISTS(SELECT 1 FROM user_role ur WHERE ur.user_id=p.user_id
                         AND ur.role_name IN ('STAFF','PURCHASER','CHEF','CLEANER'))
              AND EXISTS(SELECT 1 FROM user_canteen uc WHERE uc.user_id=p.user_id AND uc.canteen_id=p.canteen_id)
            ORDER BY p.canteen_id,u.id
            """)
    List<Map<String, Object>> all();

    @Select("SELECT * FROM staff_profile WHERE user_id=#{userId} AND canteen_id=#{canteenId}")
    Map<String, Object> one(@Param("userId") long userId, @Param("canteenId") long canteenId);

    @Select("""
            SELECT DISTINCT u.id,u.username,u.display_name,u.active FROM app_user u
            JOIN user_canteen uc ON uc.user_id=u.id JOIN user_role r ON r.user_id=u.id
            WHERE uc.canteen_id=#{canteenId} AND r.role_name IN ('STAFF','PURCHASER','CHEF','CLEANER') ORDER BY u.id
            """)
    List<Map<String, Object>> candidates(long canteenId);

    @Select("""
            SELECT COUNT(*) FROM app_user u WHERE u.id=#{userId} AND u.active=TRUE
              AND EXISTS(SELECT 1 FROM user_canteen uc WHERE uc.user_id=u.id AND uc.canteen_id=#{canteenId})
              AND EXISTS(SELECT 1 FROM user_role r WHERE r.user_id=u.id AND r.role_name IN ('STAFF','PURCHASER','CHEF','CLEANER'))
            """)
    int workerAt(@Param("userId") long userId, @Param("canteenId") long canteenId);

    @Select("SELECT id FROM app_user WHERE id=#{userId} FOR UPDATE")
    Long lockUser(long userId);

    @Select("""
            SELECT s.*,u.display_name,u.username,c.name AS canteen_name,r.display_name AS reviewer_name
            FROM staff_profile_submission s JOIN app_user u ON u.id=s.user_id JOIN canteen c ON c.id=s.canteen_id
            LEFT JOIN app_user r ON r.id=s.reviewed_by
            WHERE s.user_id=#{userId} ORDER BY s.id DESC
            """)
    List<Map<String, Object>> mine(long userId);

    @Select("""
            SELECT s.*,u.display_name,u.username,c.name AS canteen_name,r.display_name AS reviewer_name
            FROM staff_profile_submission s JOIN app_user u ON u.id=s.user_id JOIN canteen c ON c.id=s.canteen_id
            LEFT JOIN app_user r ON r.id=s.reviewed_by
            WHERE s.status IN ('PENDING','APPROVED','REJECTED')
              AND EXISTS(SELECT 1 FROM user_role ur WHERE ur.user_id=s.user_id
                         AND ur.role_name IN ('STAFF','PURCHASER','CHEF','CLEANER'))
              AND EXISTS(SELECT 1 FROM user_canteen uc WHERE uc.user_id=s.user_id AND uc.canteen_id=s.canteen_id)
            ORDER BY CASE WHEN s.status='PENDING' THEN 0 ELSE 1 END,s.id DESC
            """)
    List<Map<String, Object>> reviews();

    @Select("""
            SELECT s.*,u.display_name,u.username,c.name AS canteen_name,r.display_name AS reviewer_name
            FROM staff_profile_submission s JOIN app_user u ON u.id=s.user_id JOIN canteen c ON c.id=s.canteen_id
            LEFT JOIN app_user r ON r.id=s.reviewed_by WHERE s.id=#{id}
            """)
    Map<String, Object> submission(long id);

    @Select("SELECT * FROM staff_profile_submission WHERE id=#{id} FOR UPDATE")
    Map<String, Object> lockSubmission(long id);

    @Select("""
            SELECT * FROM staff_profile_submission WHERE user_id=#{userId} AND canteen_id=#{canteenId}
            ORDER BY id DESC LIMIT 1 FOR UPDATE
            """)
    Map<String, Object> latest(@Param("userId") long userId, @Param("canteenId") long canteenId);

    @Insert("""
            INSERT INTO staff_profile_submission(user_id,canteen_id,real_name,contact_phone,gender,position_name,
                  health_cert_no,health_expires_on,certificate_path,last_training_on,training_note)
            VALUES(#{userId},#{canteenId},#{realName},#{contactPhone},#{gender},#{positionName},#{healthCertNo},
                  #{healthExpiresOn},#{certificatePath},#{lastTrainingOn},#{trainingNote})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int createDraft(Map<String, Object> value);

    @Update("""
            UPDATE staff_profile_submission SET real_name=#{realName},contact_phone=#{contactPhone},gender=#{gender},
                position_name=#{positionName},health_cert_no=#{healthCertNo},health_expires_on=#{healthExpiresOn},
                certificate_path=#{certificatePath},last_training_on=#{lastTrainingOn},training_note=#{trainingNote},
                version=version+1
            WHERE id=#{id} AND user_id=#{userId} AND status='DRAFT' AND version=#{version}
            """)
    int updateDraft(Map<String, Object> value);

    @Update("""
            UPDATE staff_profile_submission SET status='PENDING',submitted_at=CURRENT_TIMESTAMP,version=version+1
            WHERE id=#{id} AND user_id=#{userId} AND status='DRAFT' AND version=#{version}
            """)
    int submit(@Param("id") long id, @Param("userId") long userId, @Param("version") long version);

    @Update("""
            UPDATE staff_profile_submission SET status=#{decision},reviewed_by=#{reviewedBy},reviewed_at=CURRENT_TIMESTAMP,
                review_note=#{reviewNote},version=version+1
            WHERE id=#{id} AND status='PENDING' AND version=#{version}
            """)
    int review(Map<String, Object> value);

    @Insert("""
            INSERT INTO staff_profile(user_id,canteen_id,real_name,contact_phone,gender,position_name,health_cert_no,
                    health_expires_on,certificate_path,last_training_on,training_note,updated_by,
                    approved_submission_id,approved_by,approved_at)
            SELECT user_id,canteen_id,real_name,contact_phone,gender,position_name,health_cert_no,
                    health_expires_on,certificate_path,last_training_on,training_note,#{reviewerId},
                    id,#{reviewerId},CURRENT_TIMESTAMP FROM staff_profile_submission WHERE id=#{submissionId} AND status='APPROVED'
            ON DUPLICATE KEY UPDATE real_name=VALUES(real_name),contact_phone=VALUES(contact_phone),gender=VALUES(gender),
                position_name=VALUES(position_name),health_cert_no=VALUES(health_cert_no),health_expires_on=VALUES(health_expires_on),
                certificate_path=VALUES(certificate_path),last_training_on=VALUES(last_training_on),training_note=VALUES(training_note),
                updated_by=VALUES(updated_by),approved_submission_id=VALUES(approved_submission_id),approved_by=VALUES(approved_by),
                approved_at=VALUES(approved_at)
            """)
    int approveSnapshot(@Param("submissionId") long submissionId, @Param("reviewerId") long reviewerId);

    @Select("""
            SELECT COUNT(*) FROM staff_profile p WHERE p.canteen_id=#{canteenId} AND p.approved_submission_id IS NOT NULL
              AND p.health_expires_on BETWEEN CURRENT_DATE() AND #{toDate}
              AND EXISTS(SELECT 1 FROM user_role r WHERE r.user_id=p.user_id AND r.role_name IN ('STAFF','PURCHASER','CHEF','CLEANER'))
              AND EXISTS(SELECT 1 FROM user_canteen uc WHERE uc.user_id=p.user_id AND uc.canteen_id=p.canteen_id)
            """)
    int expiringCount(@Param("canteenId") long canteenId, @Param("toDate") LocalDate toDate);
}
