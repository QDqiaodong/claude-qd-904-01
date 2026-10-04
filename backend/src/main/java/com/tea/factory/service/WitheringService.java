package com.tea.factory.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.WitheringRecord;
import com.tea.factory.dto.WitheringBoardDTO;
import com.tea.factory.dto.WitheringRequest;
import com.tea.factory.repository.WitheringRecordRepository;

/**
 * 摊青与萎凋：登记环境温湿度、失水率，达标后转为「可杀青」。
 * 每一轮摊青一条独立履历：槽号下槽（DONE）后即可承接新一批鲜叶，进行中的槽号禁止重复占用；
 * 历史 DONE 记录保留用于追溯，且不参与当前槽数与达标率统计。
 */
@Service
public class WitheringService {

    /** 当前占用态：槽号被某一轮摊青占用中 */
    private static final List<String> ACTIVE_STATUS = List.of("WITHERING", "READY");

    private final WitheringRecordRepository recordRepository;

    public WitheringService(WitheringRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /** 看板：当前占用与历史完成分开 */
    public WitheringBoardDTO board() {
        WitheringBoardDTO dto = new WitheringBoardDTO();
        dto.setActive(recordRepository.findByStatusInOrderByTroughNoAscRoundNoDesc(ACTIVE_STATUS));
        dto.setHistory(recordRepository.findByStatusOrderByTroughNoAscRoundNoDesc("DONE"));
        return dto;
    }

    public List<WitheringRecord> activeList() {
        return recordRepository.findByStatusInOrderByTroughNoAscRoundNoDesc(ACTIVE_STATUS);
    }

    public List<WitheringRecord> historyList() {
        return recordRepository.findByStatusOrderByTroughNoAscRoundNoDesc("DONE");
    }

    /** 某槽号的完整摊青履历（当前轮 + 历史轮），按轮次倒序 */
    public List<WitheringRecord> historyOfTrough(String troughNo) {
        return recordRepository.findByTroughNoOrderByRoundNoDesc(troughNo);
    }

    /** 开槽登记：已下槽槽号可承接新批次，进行中槽号禁止重复占用 */
    @Transactional
    public WitheringRecord open(WitheringRequest request) {
        String troughNo = request.getTroughNo();
        if (troughNo == null || troughNo.isBlank()) {
            throw new BizException("槽号不能为空");
        }
        troughNo = troughNo.trim();
        if (recordRepository.findByTroughNoAndStatusIn(troughNo, ACTIVE_STATUS) != null) {
            throw new BizException("槽号 " + troughNo + " 正在摊青中，请先下槽后再开新批次");
        }
        if (!Dict.isValidWitherMethod(request.getWitherMethod())) {
            throw new BizException("摊青方式只能是：" + String.join(" / ", Dict.WITHER_METHODS));
        }
        if (!Dict.isValidTeaType(request.getTeaType())) {
            throw new BizException("茶类不合法，可选：" + String.join("、", Dict.TEA_TYPES));
        }
        BigDecimal thickness = request.getLeafThicknessCm() == null ? BigDecimal.valueOf(3) : request.getLeafThicknessCm();
        if (thickness.compareTo(BigDecimal.valueOf(1)) < 0 || thickness.compareTo(BigDecimal.valueOf(15)) > 0) {
            throw new BizException("摊叶厚度应在 1~15 cm 之间，过厚会闷青、过薄会失水过快");
        }

        int roundNo = recordRepository.findFirstByTroughNoOrderByRoundNoDesc(troughNo)
                .map(last -> last.getRoundNo() + 1)
                .orElse(1);

        LocalDateTime now = LocalDateTime.now();
        WitheringRecord record = new WitheringRecord();
        record.setTroughNo(troughNo);
        record.setRoundNo(roundNo);
        record.setIntakeNo(request.getIntakeNo());
        record.setBatchNo(request.getBatchNo());
        record.setTeaType(request.getTeaType());
        record.setWitherMethod(request.getWitherMethod());
        record.setLeafThicknessCm(thickness);
        record.setWitherHours(request.getWitherHours());
        record.setTempC(request.getTempC());
        record.setHumidityPct(request.getHumidityPct());
        record.setLeafWeightKg(request.getLeafWeightKg());
        record.setCurrentLossPct(request.getCurrentLossPct() == null ? BigDecimal.ZERO : request.getCurrentLossPct());
        record.setTargetLossPct(Dict.targetLossPct(request.getTeaType()));
        record.setStatus("WITHERING");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        try {
            return recordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // 两人同时用同一槽号开槽：uk_active_trough 唯一索引只放行一人，后来者在此被挡下
            throw new BizException("槽号 " + troughNo + " 刚被其他人占用，请刷新查看当前摊青批次");
        }
    }

    /** 登记环境温湿度与失水率（只作用于该槽号的当前占用轮） */
    @Transactional
    public WitheringRecord log(String troughNo, WitheringRequest request) {
        WitheringRecord record = currentRecord(troughNo);
        if (request.getTempC() != null) {
            record.setTempC(request.getTempC());
        }
        if (request.getHumidityPct() != null) {
            record.setHumidityPct(request.getHumidityPct());
        }
        if (request.getWitherHours() != null) {
            record.setWitherHours(request.getWitherHours());
        }
        if (request.getLeafThicknessCm() != null) {
            record.setLeafThicknessCm(request.getLeafThicknessCm());
        }
        if (request.getCurrentLossPct() != null) {
            BigDecimal loss = request.getCurrentLossPct();
            if (loss.compareTo(BigDecimal.ZERO) < 0 || loss.compareTo(BigDecimal.valueOf(90)) > 0) {
                throw new BizException("失水率应在 0~90% 之间");
            }
            record.setCurrentLossPct(loss);
        }
        record.setStatus(record.getCurrentLossPct().compareTo(record.getTargetLossPct()) >= 0 ? "READY" : "WITHERING");
        record.setUpdatedAt(LocalDateTime.now());
        return recordRepository.save(record);
    }

    /** 下槽（摊青结束，鲜叶转投制茶工单）：达标后置 DONE，槽号随即释放可承接新批次 */
    @Transactional
    public WitheringRecord release(String troughNo) {
        WitheringRecord record = currentRecord(troughNo);
        if (record.getCurrentLossPct().compareTo(record.getTargetLossPct()) < 0) {
            throw new BizException("槽号 " + troughNo + " 当前失水率 " + record.getCurrentLossPct()
                    + "% 未达目标 " + record.getTargetLossPct() + "%，不允许下槽");
        }
        int updated = recordRepository.markReleased(record.getId(), LocalDateTime.now());
        if (updated == 0) {
            // 并发下槽 / 重复点击：本轮已被置为 DONE，槽号已释放
            throw new BizException("槽号 " + troughNo + " 当前轮次已下槽，请刷新查看");
        }
        return recordRepository.findById(record.getId()).orElse(record);
    }

    /** 可杀青槽列表（当前占用中已达标） */
    public List<WitheringRecord> readyList() {
        return recordRepository.findByStatusOrderByTroughNoAscRoundNoDesc("READY");
    }

    /** 取槽号的当前占用轮；没有则区分「从未登记」与「已下槽」给明确提示 */
    private WitheringRecord currentRecord(String troughNo) {
        if (troughNo == null || troughNo.isBlank()) {
            throw new BizException("槽号不能为空");
        }
        WitheringRecord record = recordRepository.findByTroughNoAndStatusIn(troughNo.trim(), ACTIVE_STATUS);
        if (record != null) {
            return record;
        }
        if (recordRepository.findFirstByTroughNoOrderByRoundNoDesc(troughNo.trim()).isPresent()) {
            throw new BizException("槽号 " + troughNo + " 当前没有在摊青的批次（上一轮已下槽），请对新批次重新开槽");
        }
        throw new BizException("槽号 " + troughNo + " 不存在，请先开槽登记");
    }
}
