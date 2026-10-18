package edu.campus.food.algorithm;

import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface AlgorithmMapper {
    @Select("SELECT version,weights_json,thresholds_json,active,created_at FROM risk_config WHERE active=TRUE ORDER BY version DESC LIMIT 1")
    Map<String, Object> activeConfig();

    @Select("SELECT COALESCE(MAX(version),0)+1 FROM risk_config")
    int nextConfigVersion();

    @Update("UPDATE risk_config SET active=FALSE WHERE active=TRUE")
    int deactivateConfigs();

    @Insert("INSERT INTO risk_config(version,weights_json,thresholds_json,active,created_by) VALUES(#{version},#{weights},#{thresholds},TRUE,#{createdBy})")
    int addConfig(Map<String, Object> value);

    @Select("SELECT id,severity,status,category,due_at,created_at FROM issue WHERE canteen_id=#{canteenId} AND created_at>=#{fromDate} ORDER BY id")
    List<Map<String, Object>> recentIssues(@Param("canteenId") long canteenId, @Param("fromDate") LocalDate fromDate);

    @Select("SELECT COUNT(*) FROM daily_check WHERE canteen_id=#{canteenId} AND check_date>=#{fromDate}")
    int dailyCount(@Param("canteenId") long canteenId, @Param("fromDate") LocalDate fromDate);

    @Select("SELECT COUNT(*) FROM inspection WHERE canteen_id=#{canteenId} AND status='COMPLETED' AND completed_at>=#{fromDate}")
    int inspectionCount(@Param("canteenId") long canteenId, @Param("fromDate") LocalDate fromDate);

    @Select("SELECT COUNT(*) FROM purchase_batch WHERE canteen_id=#{canteenId} AND received_on>=#{fromDate}")
    int batchCount(@Param("canteenId") long canteenId, @Param("fromDate") LocalDate fromDate);

    @Select("""
            SELECT COUNT(DISTINCT s.id) FROM supplier s JOIN purchase_batch b ON b.supplier_id=s.id
            WHERE b.canteen_id=#{canteenId} AND s.active=TRUE AND s.license_expires_on<CURRENT_DATE()
            """)
    int expiredSuppliers(long canteenId);

    @Select("SELECT COUNT(*) FROM purchase_batch WHERE canteen_id=#{canteenId} AND status='REJECTED' AND received_on>=#{fromDate}")
    int rejectedBatches(@Param("canteenId") long canteenId, @Param("fromDate") LocalDate fromDate);

    @Select("SELECT COUNT(*) FROM feedback WHERE canteen_id=#{canteenId} AND status IN ('NEW','PROCESSING') AND created_at>=#{fromDate}")
    int unresolvedFeedback(@Param("canteenId") long canteenId, @Param("fromDate") LocalDate fromDate);

    @Insert("""
            INSERT INTO risk_score(canteen_id,config_version,score,level,breakdown_json,window_start,window_end)
            VALUES(#{canteenId},#{configVersion},#{score},#{level},#{breakdown},#{windowStart},#{windowEnd})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int saveRisk(Map<String, Object> value);

    @Select("SELECT * FROM risk_score WHERE canteen_id=#{canteenId} ORDER BY calculated_at DESC,id DESC LIMIT 1")
    Map<String, Object> latestRisk(long canteenId);

    @Select("SELECT * FROM risk_score WHERE canteen_id=#{canteenId} ORDER BY calculated_at DESC,id DESC LIMIT 30")
    List<Map<String, Object>> riskHistory(long canteenId);

    @Select("SELECT MAX(completed_at) FROM inspection WHERE canteen_id=#{canteenId} AND status='COMPLETED'")
    java.time.LocalDateTime lastInspection(long canteenId);

    @Select("SELECT COUNT(*) FROM issue WHERE canteen_id=#{canteenId} AND status<>'CLOSED' AND severity='HIGH'")
    int openHighIssues(long canteenId);

    @Select("SELECT * FROM inspector_availability WHERE available_date BETWEEN #{fromDate} AND #{toDate} ORDER BY available_date,inspector_id")
    List<Map<String, Object>> availability(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Insert("""
            INSERT INTO inspector_availability(inspector_id,available_date,available,max_tasks)
            VALUES(#{inspectorId},#{date},#{available},#{maxTasks})
            ON DUPLICATE KEY UPDATE available=VALUES(available),max_tasks=VALUES(max_tasks)
            """)
    int upsertAvailability(Map<String, Object> value);

    @Select("""
            SELECT DATE(scheduled_at) AS task_date,inspector_id,COUNT(*) AS task_count
            FROM inspection WHERE status IN ('SCHEDULED','IN_PROGRESS') AND DATE(scheduled_at) BETWEEN #{fromDate} AND #{toDate}
            GROUP BY DATE(scheduled_at),inspector_id
            """)
    List<Map<String, Object>> existingTasks(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Select("SELECT DISTINCT canteen_id FROM inspection WHERE status IN ('SCHEDULED','IN_PROGRESS') AND DATE(scheduled_at) BETWEEN #{fromDate} AND #{toDate}")
    List<Long> scheduledCanteens(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Select("SELECT * FROM schedule_suggestion WHERE plan_date BETWEEN #{fromDate} AND #{toDate} ORDER BY priority_score DESC,id")
    List<Map<String, Object>> suggestions(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Select("SELECT * FROM schedule_suggestion WHERE id=#{id}")
    Map<String, Object> suggestion(long id);

    @Insert("""
            INSERT INTO schedule_suggestion(plan_date,canteen_id,inspector_id,priority_score,reason)
            VALUES(#{planDate},#{canteenId},#{inspectorId},#{priorityScore},#{reason})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addSuggestion(Map<String, Object> value);

    @Update("UPDATE schedule_suggestion SET plan_date=#{planDate},inspector_id=#{inspectorId} WHERE id=#{id} AND status='SUGGESTED'")
    int adjustSuggestion(Map<String, Object> value);

    @Update("UPDATE schedule_suggestion SET status='CONFIRMED',inspection_id=#{inspectionId},confirmed_at=NOW() WHERE id=#{id} AND status='SUGGESTED'")
    int confirmSuggestion(@Param("id") long id, @Param("inspectionId") long inspectionId);

    @Update("UPDATE schedule_suggestion SET status='DISMISSED' WHERE id=#{id} AND status='SUGGESTED'")
    int dismissSuggestion(long id);
}
