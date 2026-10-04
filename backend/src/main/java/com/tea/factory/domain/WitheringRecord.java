package com.tea.factory.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 摊青履历：每一轮摊青一行（同一槽号可有多条，按 round_no 递增）。
 * 状态为 WITHERING / READY 时代表该槽号的当前占用，下槽置 DONE 后槽号释放、记录保留可追溯。
 * 并发占用由数据库层 uk_active_trough 唯一索引兜底（见 schema.sql 的 active_key 生成列）。
 */
@Data
@Entity
@Table(name = "withering_record")
public class WitheringRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 槽号，如 TQ-01；同一槽号每轮摊青复用 */
    @Column(name = "trough_no", nullable = false, length = 16)
    private String troughNo;

    /** 该槽号的第几轮摊青（从 1 起） */
    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    /** 关联鲜叶进厂单 */
    @Column(name = "intake_no", length = 32)
    private String intakeNo;

    /** 关联制茶工单批次号 */
    @Column(name = "batch_no", length = 32)
    private String batchNo;

    @Column(name = "tea_type", length = 16)
    private String teaType;

    /** 摊青方式：日光晒青 / 室内萎凋 */
    @Column(name = "wither_method", length = 16)
    private String witherMethod;

    /** 摊叶厚度 cm */
    @Column(name = "leaf_thickness_cm", precision = 5, scale = 2)
    private BigDecimal leafThicknessCm;

    /** 摊青时长 h */
    @Column(name = "wither_hours", precision = 5, scale = 1)
    private BigDecimal witherHours;

    /** 环境温度 ℃ */
    @Column(name = "temp_c", precision = 5, scale = 1)
    private BigDecimal tempC;

    /** 环境相对湿度 % */
    @Column(name = "humidity_pct", precision = 5, scale = 1)
    private BigDecimal humidityPct;

    /** 当前失水率 % */
    @Column(name = "current_loss_pct", precision = 5, scale = 2)
    private BigDecimal currentLossPct;

    /** 目标失水率 % */
    @Column(name = "target_loss_pct", precision = 5, scale = 2)
    private BigDecimal targetLossPct;

    /** 在槽鲜叶重量 kg */
    @Column(name = "leaf_weight_kg", precision = 10, scale = 2)
    private BigDecimal leafWeightKg;

    /** WITHERING / READY：当前占用；DONE：已下槽（历史完成履历） */
    @Column(name = "status", length = 16)
    private String status;

    /** 开槽时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 下槽时间（历史履历时有值） */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;
}
