package edu.campus.food.auth;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {
    @Select("SELECT id, username, password_hash, display_name, identity_type, self_registered, active, created_at FROM app_user WHERE username = #{username}")
    Map<String, Object> byUsername(String username);

    @Select("SELECT id, username, display_name, identity_type, self_registered, active, created_at FROM app_user WHERE id = #{id}")
    Map<String, Object> byId(long id);

    @Select("SELECT id, username, display_name, identity_type, self_registered, active, created_at FROM app_user ORDER BY id DESC")
    List<Map<String, Object>> all();

    @Select("SELECT role_name FROM user_role WHERE user_id = #{userId} ORDER BY role_name")
    List<String> roles(long userId);

    @Select("SELECT canteen_id FROM user_canteen WHERE user_id = #{userId} ORDER BY canteen_id")
    List<Long> scopes(long userId);

    @Select("SELECT u.id, u.username, u.display_name FROM app_user u JOIN user_role r ON r.user_id=u.id WHERE r.role_name='INSPECTOR' AND u.active=TRUE ORDER BY u.id")
    List<Map<String, Object>> inspectors();

    @Insert("INSERT INTO app_user(username,password_hash,display_name,identity_type,self_registered,active) VALUES(#{username},#{passwordHash},#{displayName},#{identityType},#{selfRegistered},TRUE)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> user);

    @Insert("INSERT INTO user_role(user_id,role_name) VALUES(#{userId},#{role})")
    int addRole(@Param("userId") long userId, @Param("role") String role);

    @Insert("INSERT INTO user_canteen(user_id,canteen_id) VALUES(#{userId},#{canteenId})")
    int addScope(@Param("userId") long userId, @Param("canteenId") long canteenId);

    @Delete("DELETE FROM user_role WHERE user_id=#{userId}")
    int clearRoles(long userId);

    @Delete("DELETE FROM user_canteen WHERE user_id=#{userId}")
    int clearScopes(long userId);

    @Update("UPDATE app_user SET active=#{active} WHERE id=#{id}")
    int setActive(@Param("id") long id, @Param("active") boolean active);

    @Update("UPDATE app_user SET password_hash=#{hash} WHERE id=#{id}")
    int setPassword(@Param("id") long id, @Param("hash") String hash);
}
