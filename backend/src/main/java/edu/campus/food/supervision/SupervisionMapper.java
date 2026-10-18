package edu.campus.food.supervision;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface SupervisionMapper {
    @Select("""
            SELECT d.*,c.name AS canteen_name,u.display_name AS checker_name
            FROM daily_check d JOIN canteen c ON c.id=d.canteen_id
            JOIN app_user u ON u.id=d.checked_by ORDER BY d.check_date DESC,d.id DESC
            """)
    List<Map<String, Object>> dailyChecks();

    @Select("SELECT * FROM daily_check WHERE id=#{id}")
    Map<String, Object> dailyCheck(long id);

    @Select("SELECT * FROM daily_check_result WHERE daily_check_id=#{id} ORDER BY id")
    List<Map<String, Object>> dailyResults(long id);

    @Insert("INSERT INTO daily_check(canteen_id,template_id,check_date,checked_by,note) VALUES(#{canteenId},#{templateId},#{checkDate},#{checkedBy},#{note})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addDailyCheck(Map<String, Object> values);

    @Insert("INSERT INTO daily_check_result(daily_check_id,item_id,item_title,result,note) VALUES(#{dailyCheckId},#{itemId},#{itemTitle},#{result},#{note})")
    int addDailyResult(Map<String, Object> values);

    @Select("""
            SELECT i.*,c.name AS canteen_name,u.display_name AS inspector_name
            FROM inspection i JOIN canteen c ON c.id=i.canteen_id
            JOIN app_user u ON u.id=i.inspector_id ORDER BY i.scheduled_at DESC,i.id DESC
            """)
    List<Map<String, Object>> inspections();

    @Select("SELECT * FROM inspection WHERE id=#{id}")
    Map<String, Object> inspection(long id);

    @Insert("INSERT INTO inspection(canteen_id,inspector_id,template_id,scheduled_at,note,created_by) VALUES(#{canteenId},#{inspectorId},#{templateId},#{scheduledAt},#{note},#{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addInspection(Map<String, Object> values);

    @Select("SELECT * FROM inspection_result WHERE inspection_id=#{inspectionId} ORDER BY id")
    List<Map<String, Object>> inspectionResults(long inspectionId);

    @Insert("INSERT INTO inspection_result(inspection_id,item_id,item_title,result,note) VALUES(#{inspectionId},#{itemId},#{itemTitle},#{result},#{note})")
    int addInspectionResult(Map<String, Object> values);

    @Update("UPDATE inspection SET status='IN_PROGRESS',started_at=NOW() WHERE id=#{id} AND status='SCHEDULED'")
    int startInspection(long id);

    @Update("UPDATE inspection SET status='COMPLETED',completed_at=NOW(),note=#{note} WHERE id=#{id} AND status='IN_PROGRESS'")
    int completeInspection(@Param("id") long id, @Param("note") String note);

    @Update("UPDATE inspection SET status='CANCELLED',cancel_reason=#{reason} WHERE id=#{id} AND status='SCHEDULED'")
    int cancelInspection(@Param("id") long id, @Param("reason") String reason);

    @Select("""
            SELECT i.*,c.name AS canteen_name,a.display_name AS assignee_name,u.display_name AS creator_name,
                   CASE WHEN i.status<>'CLOSED' AND i.due_at<NOW() THEN TRUE ELSE FALSE END AS overdue
            FROM issue i JOIN canteen c ON c.id=i.canteen_id
            JOIN app_user a ON a.id=i.assigned_to JOIN app_user u ON u.id=i.created_by
            ORDER BY i.created_at DESC,i.id DESC
            """)
    List<Map<String, Object>> issues();

    @Select("SELECT * FROM issue WHERE id=#{id}")
    Map<String, Object> issue(long id);

    @Select("SELECT * FROM issue WHERE id=#{id} FOR UPDATE")
    Map<String, Object> lockIssue(long id);

    @Select("SELECT e.*,u.display_name AS actor_name FROM issue_event e JOIN app_user u ON u.id=e.actor_id WHERE e.issue_id=#{issueId} ORDER BY e.id")
    List<Map<String, Object>> issueEvents(long issueId);

    @Select("SELECT o.*,u.display_name AS principal_name FROM issue_oversight o JOIN app_user u ON u.id=o.principal_id WHERE o.issue_id=#{issueId} ORDER BY o.id DESC")
    List<Map<String, Object>> oversights(long issueId);

    @Select("SELECT * FROM issue_oversight WHERE id=#{id}")
    Map<String, Object> oversight(long id);

    @Insert("INSERT INTO issue_oversight(issue_id,principal_id,note) VALUES(#{issueId},#{principalId},#{note})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addOversight(Map<String, Object> value);

    @Update("UPDATE issue_oversight SET status='CLOSED',close_note=#{note},closed_at=NOW() WHERE id=#{id} AND status='OPEN'")
    int closeOversight(@Param("id") long id, @Param("note") String note);

    @Insert("INSERT INTO issue_event(issue_id,actor_id,event_type,note) VALUES(#{issueId},#{actorId},#{eventType},#{note})")
    int addIssueEvent(Map<String, Object> values);

    @Insert("""
            INSERT INTO issue(canteen_id,source_type,source_id,category,severity,description,location,evidence_path,assigned_to,
                              due_at,needs_report,report_note,created_by)
            VALUES(#{canteenId},#{sourceType},#{sourceId},#{category},#{severity},#{description},#{location},#{evidencePath},#{assignedTo},
                   #{dueAt},#{needsReport},#{reportNote},#{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addIssue(Map<String, Object> values);

    @Update("UPDATE issue SET status=#{status},closed_at=CASE WHEN #{status}='CLOSED' THEN NOW() ELSE NULL END WHERE id=#{id}")
    int setIssueStatus(@Param("id") long id, @Param("status") String status);

    @Select("""
            SELECT r.*,u.display_name AS submitter_name,v.display_name AS reviewer_name
            FROM rectification r JOIN app_user u ON u.id=r.submitter_id
            LEFT JOIN app_user v ON v.id=r.reviewed_by WHERE r.issue_id=#{issueId} ORDER BY r.id DESC
            """)
    List<Map<String, Object>> rectifications(long issueId);

    @Select("SELECT * FROM rectification WHERE issue_id=#{issueId} ORDER BY id DESC LIMIT 1 FOR UPDATE")
    Map<String, Object> latestRectification(long issueId);

    @Insert("INSERT INTO rectification(issue_id,submitter_id,details,evidence_path) VALUES(#{issueId},#{submitterId},#{details},#{evidencePath})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addRectification(Map<String, Object> values);

    @Update("UPDATE rectification SET review_result=#{result},reviewed_by=#{reviewedBy},review_note=#{note},reviewed_at=NOW() WHERE id=#{id} AND review_result IS NULL")
    int reviewRectification(Map<String, Object> values);

    @Select("""
            SELECT f.*,c.name AS canteen_name,u.display_name AS author_name,a.display_name AS assignee_name
            FROM feedback f JOIN canteen c ON c.id=f.canteen_id
            JOIN app_user u ON u.id=f.user_id LEFT JOIN app_user a ON a.id=f.assigned_to
            ORDER BY f.created_at DESC,f.id DESC
            """)
    List<Map<String, Object>> feedbacks();

    @Select("""
            SELECT f.*,c.name AS canteen_name,u.display_name AS author_name,
                   a.display_name AS assignee_name,i.display_name AS investigator_name
            FROM feedback f JOIN canteen c ON c.id=f.canteen_id
            JOIN app_user u ON u.id=f.user_id LEFT JOIN app_user a ON a.id=f.assigned_to
            LEFT JOIN app_user i ON i.id=f.investigated_by WHERE f.id=#{id}
            """)
    Map<String, Object> feedback(long id);

    @Select("SELECT id FROM feedback WHERE user_id=#{userId} AND canteen_id=#{canteenId} AND description=#{description} AND created_at>DATE_SUB(NOW(),INTERVAL 5 MINUTE) LIMIT 1")
    Long recentDuplicateFeedback(@Param("userId") long userId, @Param("canteenId") long canteenId,
                                 @Param("description") String description);

    @Insert("""
            INSERT INTO feedback(user_id,canteen_id,occurred_at,category,description,image_path)
            VALUES(#{userId},#{canteenId},#{occurredAt},#{category},#{description},#{imagePath})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addFeedback(Map<String, Object> values);

    @Update("UPDATE feedback SET status='PROCESSING',assigned_to=#{assignedTo} WHERE id=#{id} AND status='NEW'")
    int assignFeedback(@Param("id") long id, @Param("assignedTo") long assignedTo);

    @Update("UPDATE feedback SET investigation_note=#{note},investigated_by=#{investigatedBy},investigated_at=NOW() WHERE id=#{id} AND status='PROCESSING' AND investigation_note IS NULL")
    int investigateFeedback(@Param("id") long id, @Param("note") String note, @Param("investigatedBy") long investigatedBy);

    @Update("UPDATE feedback SET status='RESPONDED',response=#{response},responded_at=NOW() WHERE id=#{id} AND status='PROCESSING'")
    int respondFeedback(@Param("id") long id, @Param("response") String response);

    @Update("UPDATE feedback SET status='CLOSED' WHERE id=#{id} AND status='RESPONDED'")
    int closeFeedback(long id);

    @Update("UPDATE feedback SET status='PROCESSING',investigation_note=NULL,investigated_by=NULL,investigated_at=NULL,response=NULL,responded_at=NULL WHERE id=#{id} AND status IN ('RESPONDED','CLOSED')")
    int reopenFeedback(long id);

    @Update("UPDATE feedback SET issue_id=#{issueId} WHERE id=#{id}")
    int linkFeedbackIssue(@Param("id") long id, @Param("issueId") long issueId);

    @Select("SELECT COUNT(*) FROM feedback WHERE status='NEW'")
    int newFeedbackCount();

    @Select("SELECT COUNT(*) FROM issue WHERE status<>'CLOSED' AND due_at<NOW()")
    int overdueIssueCount();

    @Select("SELECT COUNT(*) FROM issue WHERE status<>'CLOSED'")
    int openIssueCount();

    @Select("SELECT COUNT(*) FROM purchase_batch WHERE status='REJECTED'")
    int rejectedBatchCount();

    @Select("SELECT canteen_id,COUNT(*) AS total,SUM(status='CLOSED') AS closed FROM issue GROUP BY canteen_id")
    List<Map<String, Object>> issueSummary();

    @Select("SELECT canteen_id,COUNT(*) AS total FROM inspection WHERE status='COMPLETED' GROUP BY canteen_id")
    List<Map<String, Object>> inspectionSummary();

    @Select("SELECT canteen_id,COUNT(*) AS total FROM feedback GROUP BY canteen_id")
    List<Map<String, Object>> feedbackSummary();
}
