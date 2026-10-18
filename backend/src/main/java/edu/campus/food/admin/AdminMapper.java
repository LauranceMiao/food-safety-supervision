package edu.campus.food.admin;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminMapper {
    @Select("SELECT id,name,location,active,created_at FROM canteen ORDER BY id")
    List<Map<String, Object>> canteens();

    @Select("SELECT id,name,location,active FROM canteen WHERE id=#{id}")
    Map<String, Object> canteen(long id);

    @Insert("INSERT INTO canteen(name,location) VALUES(#{name},#{location})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addCanteen(Map<String, Object> values);

    @Update("UPDATE canteen SET name=#{name},location=#{location},active=#{active} WHERE id=#{id}")
    int updateCanteen(Map<String, Object> values);

    @Select("SELECT s.id,s.canteen_id,s.name,s.active,c.name AS canteen_name FROM stall s JOIN canteen c ON c.id=s.canteen_id ORDER BY s.canteen_id,s.id")
    List<Map<String, Object>> stalls();

    @Insert("INSERT INTO stall(canteen_id,name) VALUES(#{canteenId},#{name})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addStall(Map<String, Object> values);

    @Select("SELECT id,canteen_id,name,active FROM stall WHERE id=#{id}")
    Map<String, Object> stall(long id);

    @Update("UPDATE stall SET name=#{name},active=#{active} WHERE id=#{id}")
    int updateStall(Map<String, Object> values);

    @Select("SELECT t.id,t.name,t.active FROM check_template t ORDER BY t.id")
    List<Map<String, Object>> templates();

    @Select("SELECT id,name,active FROM check_template WHERE id=#{id}")
    Map<String, Object> template(long id);

    @Select("SELECT id,template_id,title,sort_order,active FROM check_item WHERE template_id=#{templateId} ORDER BY sort_order,id")
    List<Map<String, Object>> templateItems(long templateId);

    @Insert("INSERT INTO check_template(name) VALUES(#{name})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addTemplate(Map<String, Object> values);

    @Insert("INSERT INTO check_item(template_id,title,sort_order) VALUES(#{templateId},#{title},#{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addTemplateItem(Map<String, Object> values);

    @Update("UPDATE check_template SET active=#{active} WHERE id=#{id}")
    int setTemplateActive(@Param("id") long id, @Param("active") boolean active);

    @Update("UPDATE check_item SET active=#{active} WHERE id=#{id}")
    int setTemplateItemActive(@Param("id") long id, @Param("active") boolean active);

    @Select("SELECT COUNT(*) FROM app_user")
    int userCount();

    @Select("SELECT COUNT(*) FROM risk_config")
    int configCount();

    @Insert("INSERT INTO risk_config(version,weights_json,thresholds_json,active,created_by) VALUES(1,#{weights},#{thresholds},TRUE,#{createdBy})")
    int insertDefaultConfig(@Param("weights") String weights, @Param("thresholds") String thresholds, @Param("createdBy") long createdBy);
}
