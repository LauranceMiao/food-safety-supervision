package edu.campus.food.file;

import org.apache.ibatis.annotations.*;

import java.util.Map;
import java.util.List;

@Mapper
public interface FileMapper {
    @Insert("INSERT INTO uploaded_file(id,purpose,canteen_id,owner_id,original_name,content_type,size_bytes) VALUES(#{id},#{purpose},#{canteenId},#{ownerId},#{originalName},#{contentType},#{sizeBytes})")
    int insert(Map<String, Object> value);

    @Select("SELECT * FROM uploaded_file WHERE id=#{id}")
    Map<String, Object> byId(String id);

    @Select("SELECT f.id,f.assigned_to FROM feedback f WHERE f.image_path=#{path} LIMIT 1")
    Map<String, Object> assignedFeedback(String path);

    @Select("""
            SELECT p.user_id,p.canteen_id,'APPROVED' AS status FROM staff_profile p
            WHERE p.certificate_path=#{path} AND p.approved_submission_id IS NOT NULL
              AND EXISTS(SELECT 1 FROM user_role r WHERE r.user_id=p.user_id AND r.role_name IN ('STAFF','PURCHASER','CHEF','CLEANER'))
              AND EXISTS(SELECT 1 FROM user_canteen c WHERE c.user_id=p.user_id AND c.canteen_id=p.canteen_id)
            UNION ALL
            SELECT s.user_id,s.canteen_id,s.status FROM staff_profile_submission s WHERE s.certificate_path=#{path}
              AND EXISTS(SELECT 1 FROM user_role r WHERE r.user_id=s.user_id AND r.role_name IN ('STAFF','PURCHASER','CHEF','CLEANER'))
              AND EXISTS(SELECT 1 FROM user_canteen c WHERE c.user_id=s.user_id AND c.canteen_id=s.canteen_id)
            """)
    List<Map<String, Object>> healthCertificateLinks(String path);

    @Select("""
            SELECT (SELECT COUNT(*) FROM staff_profile WHERE certificate_path=#{path})
                 + (SELECT COUNT(*) FROM staff_profile_submission WHERE certificate_path=#{path})
            """)
    int healthCertificateUseCount(String path);

    @Select("""
            SELECT i.id,i.assigned_to FROM issue i WHERE i.evidence_path=#{path}
            UNION ALL
            SELECT i.id,i.assigned_to FROM issue i JOIN rectification r ON r.issue_id=i.id WHERE r.evidence_path=#{path}
            LIMIT 1
            """)
    Map<String, Object> assignedIssue(String path);
}
