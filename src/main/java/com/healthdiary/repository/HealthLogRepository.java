package com.healthdiary.repository;

import com.healthdiary.entity.HealthLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface HealthLogRepository extends JpaRepository<HealthLog, Long> {

    List<HealthLog> findAllByOrderByLogDateAsc();

    List<HealthLog> findAllByOrderByLogDateDesc();

    List<HealthLog> findByIsSeedDataTrue();

    List<HealthLog> findByIsSeedDataFalseOrderByLogDateDesc();

    @Query("select h from HealthLog h where h.logDate between :from and :to order by h.logDate asc")
    List<HealthLog> findByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByIsSeedDataTrue();
}
