package edu.campus.food.catalog;

import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface CatalogMapper {
    @Select("SELECT COUNT(*) FROM purchase_batch WHERE plan_id=#{planId}")
    int planBatchCount(long planId);
    @Select("SELECT s.*,u.display_name AS creator_name FROM supplier s JOIN app_user u ON u.id=s.created_by ORDER BY s.id DESC")
    List<Map<String, Object>> suppliers();

    @Select("SELECT * FROM supplier WHERE id=#{id}")
    Map<String, Object> supplier(long id);

    @Insert("INSERT INTO supplier(name,license_no,license_expires_on,contact_name,contact_phone,certificate_path,created_by) VALUES(#{name},#{licenseNo},#{licenseExpiresOn},#{contactName},#{contactPhone},#{certificatePath},#{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addSupplier(Map<String, Object> values);

    @Update("UPDATE supplier SET name=#{name},license_no=#{licenseNo},license_expires_on=#{licenseExpiresOn},contact_name=#{contactName},contact_phone=#{contactPhone},certificate_path=#{certificatePath},active=#{active} WHERE id=#{id}")
    int updateSupplier(Map<String, Object> values);

    @Select("SELECT * FROM ingredient ORDER BY id DESC")
    List<Map<String, Object>> ingredients();

    @Select("SELECT * FROM ingredient WHERE id=#{id}")
    Map<String, Object> ingredient(long id);

    @Select("""
            SELECT p.*,c.name AS canteen_name,i.name AS ingredient_name,i.unit AS ingredient_unit,
                   s.name AS supplier_name,COALESCE(SUM(b.quantity),0) AS received_quantity,
                   COALESCE(SUM(CASE WHEN b.status='ACCEPTED' THEN b.quantity ELSE 0 END),0) AS accepted_quantity
            FROM purchase_plan p JOIN canteen c ON c.id=p.canteen_id
            JOIN ingredient i ON i.id=p.ingredient_id LEFT JOIN supplier s ON s.id=p.supplier_id
            LEFT JOIN purchase_batch b ON b.plan_id=p.id
            GROUP BY p.id,c.name,i.name,i.unit,s.name ORDER BY p.planned_on DESC,p.id DESC
            """)
    List<Map<String, Object>> plans();

    @Select("SELECT * FROM purchase_plan WHERE id=#{id}")
    Map<String, Object> plan(long id);

    @Select("SELECT * FROM purchase_plan WHERE id=#{id} FOR UPDATE")
    Map<String, Object> lockPlan(long id);

    @Insert("INSERT INTO purchase_plan(canteen_id,ingredient_id,supplier_id,planned_on,planned_quantity,created_by) VALUES(#{canteenId},#{ingredientId},#{supplierId},#{plannedOn},#{plannedQuantity},#{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addPlan(Map<String, Object> value);

    @Update("""
            UPDATE purchase_plan SET published_at=CURRENT_TIMESTAMP,published_by=#{userId}
            WHERE id=#{id} AND status='OPEN' AND published_at IS NULL
            """)
    int publishPlan(@Param("id") long id, @Param("userId") long userId);

    @Update("UPDATE purchase_plan SET status=#{status} WHERE id=#{id} AND status='OPEN'")
    int setPlanStatus(@Param("id") long id, @Param("status") String status);

    @Insert("INSERT INTO ingredient(name,category,unit) VALUES(#{name},#{category},#{unit})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addIngredient(Map<String, Object> values);

    @Select("""
            SELECT b.*,c.name AS canteen_name,i.name AS ingredient_name,i.unit AS ingredient_unit,
                   s.name AS supplier_name,u.display_name AS creator_name
            FROM purchase_batch b JOIN canteen c ON c.id=b.canteen_id
            JOIN ingredient i ON i.id=b.ingredient_id JOIN supplier s ON s.id=b.supplier_id
            JOIN app_user u ON u.id=b.created_by ORDER BY b.id DESC
            """)
    List<Map<String, Object>> batches();

    @Select("""
            SELECT b.*,c.name AS canteen_name,i.name AS ingredient_name,i.unit AS ingredient_unit,
                   s.name AS supplier_name,s.license_no,s.license_expires_on,s.contact_name,s.contact_phone
            FROM purchase_batch b JOIN canteen c ON c.id=b.canteen_id
            JOIN ingredient i ON i.id=b.ingredient_id JOIN supplier s ON s.id=b.supplier_id WHERE b.id=#{id}
            """)
    Map<String, Object> batch(long id);

    @Select("SELECT * FROM purchase_batch WHERE id=#{id} FOR UPDATE")
    Map<String, Object> lockBatch(long id);

    @Insert("""
            INSERT INTO purchase_batch(batch_code,canteen_id,ingredient_id,specification,supplier_id,plan_id,production_date,expires_on,
              received_on,quantity,available_quantity,voucher_path,created_by)
            VALUES(#{batchCode},#{canteenId},#{ingredientId},#{specification},#{supplierId},#{planId},#{productionDate},#{expiresOn},
              #{receivedOn},#{quantity},#{quantity},#{voucherPath},#{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addBatch(Map<String, Object> values);

    @Update("UPDATE purchase_batch SET batch_code=#{code} WHERE id=#{id}")
    int setBatchCode(@Param("id") long id, @Param("code") String code);

    @Update("UPDATE purchase_batch SET status=#{status} WHERE id=#{id}")
    int setBatchStatus(@Param("id") long id, @Param("status") String status);

    @Update("UPDATE purchase_batch SET available_quantity=available_quantity-#{quantity} WHERE id=#{id} AND available_quantity>=#{quantity}")
    int consumeBatch(@Param("id") long id, @Param("quantity") BigDecimal quantity);

    @Select("""
            SELECT a.*,u.display_name AS checker_name FROM acceptance a
            JOIN app_user u ON u.id=a.checked_by WHERE a.batch_id=#{batchId} ORDER BY a.id DESC
            """)
    List<Map<String, Object>> acceptances(long batchId);

    @Insert("INSERT INTO acceptance(batch_id,result,note,evidence_path,checked_by) VALUES(#{batchId},#{result},#{note},#{evidencePath},#{checkedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addAcceptance(Map<String, Object> values);

    @Select("SELECT d.*,c.name AS canteen_name FROM dish d JOIN canteen c ON c.id=d.canteen_id ORDER BY d.canteen_id,d.id DESC")
    List<Map<String, Object>> dishes();

    @Select("SELECT * FROM dish WHERE id=#{id}")
    Map<String, Object> dish(long id);

    @Insert("INSERT INTO dish(canteen_id,name,description) VALUES(#{canteenId},#{name},#{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addDish(Map<String, Object> values);

    @Select("SELECT i.id,i.name,i.unit FROM dish_ingredient di JOIN ingredient i ON i.id=di.ingredient_id WHERE di.dish_id=#{dishId} ORDER BY i.name")
    List<Map<String, Object>> dishIngredients(long dishId);

    @Insert("INSERT IGNORE INTO dish_ingredient(dish_id,ingredient_id) VALUES(#{dishId},#{ingredientId})")
    int addDishIngredient(@Param("dishId") long dishId, @Param("ingredientId") long ingredientId);

    @Select("""
            SELECT m.*,c.name AS canteen_name,d.name AS dish_name,s.name AS stall_name,
                   maker.display_name AS prepared_by_name
            FROM menu_entry m JOIN canteen c ON c.id=m.canteen_id JOIN dish d ON d.id=m.dish_id
            LEFT JOIN stall s ON s.id=m.stall_id
            LEFT JOIN app_user maker ON maker.id=m.prepared_by
            ORDER BY m.service_date DESC,m.meal,m.id
            """)
    List<Map<String, Object>> menus();

    @Select("SELECT * FROM menu_entry WHERE id=#{id}")
    Map<String, Object> menu(long id);

    @Select("SELECT * FROM menu_entry WHERE id=#{id} FOR UPDATE")
    Map<String, Object> lockMenu(long id);

    @Select("SELECT canteen_id FROM stall WHERE id=#{id}")
    Long stall(long id);

    @Insert("""
            INSERT INTO menu_entry(canteen_id,stall_id,dish_id,service_date,meal,target_portions,cooking_note,created_by)
            VALUES(#{canteenId},#{stallId},#{dishId},#{serviceDate},#{meal},#{targetPortions},#{cookingNote},#{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addMenu(Map<String, Object> values);

    @Select("""
            SELECT COUNT(*) FROM menu_entry WHERE canteen_id=#{canteenId} AND service_date=#{serviceDate}
              AND meal=#{meal} AND dish_id=#{dishId}
              AND stall_id <=> #{stallId}
              AND status<>'CANCELLED' AND (#{id} IS NULL OR id<>#{id})
            """)
    int menuExists(Map<String, Object> values);

    @Update("""
            UPDATE menu_entry SET stall_id=#{stallId},dish_id=#{dishId},meal=#{meal},
                                  target_portions=#{targetPortions},cooking_note=#{cookingNote}
            WHERE id=#{id} AND status='PLANNED' AND published_at IS NULL
            """)
    int updateDraftMenu(Map<String, Object> values);

    @Update("""
            UPDATE menu_entry SET published_at=CURRENT_TIMESTAMP,published_by=#{userId}
            WHERE canteen_id=#{canteenId} AND service_date=#{serviceDate}
              AND status='PLANNED' AND published_at IS NULL
            """)
    int publishDaily(@Param("canteenId") long canteenId, @Param("serviceDate") java.time.LocalDate serviceDate,
                     @Param("userId") long userId);

    @Update("""
            UPDATE menu_entry SET prepared_at=CURRENT_TIMESTAMP,prepared_by=#{userId}
            WHERE id=#{id} AND status='PLANNED' AND published_at IS NOT NULL AND prepared_at IS NULL
            """)
    int markPrepared(@Param("id") long id, @Param("userId") long userId);

    @Update("UPDATE menu_entry SET status=#{status} WHERE id=#{id}")
    int setMenuStatus(@Param("id") long id, @Param("status") String status);

    @Update("UPDATE menu_entry SET status='CANCELLED',cancel_reason=#{reason} WHERE id=#{id} AND status='PLANNED'")
    int cancelMenu(@Param("id") long id, @Param("reason") String reason);

    @Select("""
            SELECT bu.*,m.service_date,m.meal,m.canteen_id,m.stall_id,m.status AS menu_status,
                   d.name AS dish_name,c.name AS canteen_name,s.name AS stall_name
            FROM batch_usage bu JOIN menu_entry m ON m.id=bu.menu_entry_id
            JOIN dish d ON d.id=m.dish_id JOIN canteen c ON c.id=m.canteen_id
            LEFT JOIN stall s ON s.id=m.stall_id WHERE bu.batch_id=#{batchId} ORDER BY m.service_date,m.meal
            """)
    List<Map<String, Object>> usagesByBatch(long batchId);

    @Select("""
            SELECT bu.*,b.batch_code,i.name AS ingredient_name,i.unit
            FROM batch_usage bu JOIN purchase_batch b ON b.id=bu.batch_id
            JOIN ingredient i ON i.id=b.ingredient_id WHERE bu.menu_entry_id=#{menuId} ORDER BY bu.id
            """)
    List<Map<String, Object>> usagesByMenu(long menuId);

    @Insert("INSERT INTO batch_usage(batch_id,menu_entry_id,quantity,recorded_by) VALUES(#{batchId},#{menuEntryId},#{quantity},#{recordedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addUsage(Map<String, Object> values);
}
