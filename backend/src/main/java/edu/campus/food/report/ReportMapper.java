package edu.campus.food.report;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface ReportMapper {
    @Select("SELECT COUNT(*) FROM inspection WHERE canteen_id=#{canteenId} AND status='COMPLETED' AND completed_at>=#{from} AND completed_at<#{to}")
    int completedInspections(@Param("canteenId") long canteenId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT COUNT(*) FROM issue WHERE canteen_id=#{canteenId} AND created_at>=#{from} AND created_at<#{to}")
    int issues(@Param("canteenId") long canteenId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT COUNT(*) FROM issue WHERE canteen_id=#{canteenId} AND status='CLOSED' AND created_at>=#{from} AND created_at<#{to}")
    int closedIssues(@Param("canteenId") long canteenId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT COUNT(*) FROM issue WHERE canteen_id=#{canteenId} AND status<>'CLOSED' AND due_at<NOW() AND created_at>=#{from} AND created_at<#{to}")
    int overdueIssues(@Param("canteenId") long canteenId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT COUNT(*) FROM purchase_batch WHERE canteen_id=#{canteenId} AND status='REJECTED' AND received_on>=#{from} AND received_on<#{to}")
    int rejectedBatches(@Param("canteenId") long canteenId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT COUNT(*) FROM feedback WHERE canteen_id=#{canteenId} AND created_at>=#{from} AND created_at<#{to}")
    int feedbacks(@Param("canteenId") long canteenId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT COUNT(*) FROM feedback WHERE canteen_id=#{canteenId} AND status IN ('RESPONDED','CLOSED') AND created_at>=#{from} AND created_at<#{to}")
    int handledFeedbacks(@Param("canteenId") long canteenId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
