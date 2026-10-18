package edu.campus.food.operation;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface OperationMapper {
    @Select("""
            SELECT o.*,c.name AS canteen_name,u.display_name AS recorder_name
            FROM operation_log o JOIN canteen c ON c.id=o.canteen_id
            JOIN app_user u ON u.id=o.recorded_by
            ORDER BY o.operation_date DESC,o.id DESC LIMIT 500
            """)
    List<Map<String, Object>> all();

    @Select("SELECT * FROM operation_log WHERE id=#{id}")
    Map<String, Object> one(long id);

    @Insert("INSERT INTO operation_log(canteen_id,category,operation_date,meal,details_json,recorded_by) VALUES(#{canteenId},#{category},#{operationDate},#{meal},#{detailsJson},#{recordedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int add(Map<String, Object> value);
}
