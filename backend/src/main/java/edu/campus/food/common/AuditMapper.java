package edu.campus.food.common;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuditMapper {
    @Insert("INSERT INTO audit_log(actor_id,action,entity_type,entity_id,detail,ip_address) VALUES(#{actorId},#{action},#{entityType},#{entityId},#{detail},#{ipAddress})")
    int insert(@Param("actorId") Long actorId, @Param("action") String action,
               @Param("entityType") String entityType, @Param("entityId") Long entityId,
               @Param("detail") String detail, @Param("ipAddress") String ipAddress);

    @Select("SELECT l.*,u.username AS actor_name FROM audit_log l LEFT JOIN app_user u ON u.id=l.actor_id ORDER BY l.id DESC LIMIT #{limit}")
    List<Map<String, Object>> recent(int limit);
}
