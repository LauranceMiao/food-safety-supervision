package edu.campus.food.review;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewMapper {
    @Select("""
            SELECT r.*,c.name AS canteen_name,u.display_name AS creator_name,v.display_name AS confirmer_name
            FROM period_review r LEFT JOIN canteen c ON c.id=r.canteen_id
            JOIN app_user u ON u.id=r.created_by LEFT JOIN app_user v ON v.id=r.confirmed_by
            ORDER BY r.id DESC LIMIT 300
            """)
    List<Map<String, Object>> all();

    @Select("SELECT * FROM period_review WHERE id=#{id}")
    Map<String, Object> one(long id);

    @Insert("INSERT INTO period_review(period_type,period_start,period_end,canteen_id,summary_json,created_by) VALUES(#{periodType},#{periodStart},#{periodEnd},#{canteenId},#{summaryJson},#{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int add(Map<String, Object> value);

    @Update("UPDATE period_review SET status='CONFIRMED',conclusion=#{conclusion},confirmed_by=#{confirmedBy},confirmed_at=NOW() WHERE id=#{id} AND status='DRAFT'")
    int confirm(@Param("id") long id, @Param("conclusion") String conclusion, @Param("confirmedBy") long confirmedBy);
}
