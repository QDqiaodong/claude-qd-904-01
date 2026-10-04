package com.tea.factory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.tea.factory.domain.WitheringRecord;

public interface WitheringRecordRepository extends JpaRepository<WitheringRecord, Long> {

    /** 槽号的当前占用记录（WITHERING / READY）；uk_active_trough 保证至多一条 */
    WitheringRecord findByTroughNoAndStatusIn(String troughNo, List<String> statuses);

    Optional<WitheringRecord> findFirstByTroughNoOrderByRoundNoDesc(String troughNo);

    List<WitheringRecord> findByStatusInOrderByTroughNoAscRoundNoDesc(List<String> statuses);

    List<WitheringRecord> findByStatusOrderByTroughNoAscRoundNoDesc(String status);

    List<WitheringRecord> findByTroughNoOrderByRoundNoDesc(String troughNo);

    long countByStatusIn(List<String> statuses);

    long countByStatus(String status);

    /**
     * 手写 JPQL：摊青达标率统计（按茶类汇总当前占用轮次的槽数、已达标槽数、平均失水率）。
     * 只统计当前占用（WITHERING / READY），已下槽 DONE 履历不计入当前达标槽数。
     */
    @Query("select r.teaType, count(r), sum(case when r.currentLossPct >= r.targetLossPct then 1 else 0 end), "
            + "avg(r.currentLossPct) "
            + "from WitheringRecord r "
            + "where r.status in ('WITHERING', 'READY') "
            + "group by r.teaType")
    List<Object[]> summarizeReadyRate();

    /**
     * 条件下槽：只有仍处于占用态且失水率达标时更新才生效，返回生效行数。
     * 用于防重复下槽与并发释放（同一轮第二次执行会更新 0 行）。
     */
    @Modifying(clearAutomatically = true)
    @Query("update WitheringRecord r set r.status = 'DONE', r.releasedAt = :releasedAt, r.updatedAt = :releasedAt "
            + "where r.id = :id and r.status in ('WITHERING', 'READY') "
            + "and r.currentLossPct >= r.targetLossPct")
    int markReleased(Long id, java.time.LocalDateTime releasedAt);
}
