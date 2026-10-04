package com.tea.factory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tea.factory.common.ApiResponse;
import com.tea.factory.domain.WitheringRecord;
import com.tea.factory.dto.WitheringBoardDTO;
import com.tea.factory.dto.WitheringRequest;
import com.tea.factory.service.WitheringService;

/**
 * 摊青与萎凋。
 * 当前占用（active）与历史完成（history）分接口返回；某槽号的完整履历可单独追溯。
 */
@RestController
@RequestMapping("/api/withering")
public class WitheringController {

    private final WitheringService witheringService;

    public WitheringController(WitheringService witheringService) {
        this.witheringService = witheringService;
    }

    /** 看板：当前占用 + 历史完成 */
    @GetMapping
    public ApiResponse<WitheringBoardDTO> board() {
        return ApiResponse.success(witheringService.board());
    }

    /** 当前占用槽（摊青中 / 可杀青） */
    @GetMapping("/active")
    public ApiResponse<List<WitheringRecord>> active() {
        return ApiResponse.success(witheringService.activeList());
    }

    /** 历史完成履历（已下槽） */
    @GetMapping("/history")
    public ApiResponse<List<WitheringRecord>> history() {
        return ApiResponse.success(witheringService.historyList());
    }

    /** 某槽号的各轮摊青履历（按轮次倒序） */
    @GetMapping("/trough/{troughNo}/history")
    public ApiResponse<List<WitheringRecord>> troughHistory(@PathVariable String troughNo) {
        return ApiResponse.success(witheringService.historyOfTrough(troughNo));
    }

    /** 可杀青槽（当前占用且达标） */
    @GetMapping("/ready")
    public ApiResponse<List<WitheringRecord>> ready() {
        return ApiResponse.success(witheringService.readyList());
    }

    /** 开槽登记 */
    @PostMapping
    public ApiResponse<WitheringRecord> open(@RequestBody WitheringRequest request) {
        WitheringRecord record = witheringService.open(request);
        return ApiResponse.success("槽号 " + record.getTroughNo() + " 第 " + record.getRoundNo()
                + " 轮摊青已开槽", record);
    }

    /** 登记环境温湿度与失水率 */
    @PutMapping("/{troughNo}/log")
    public ApiResponse<WitheringRecord> log(@PathVariable String troughNo, @RequestBody WitheringRequest request) {
        WitheringRecord record = witheringService.log(troughNo, request);
        return ApiResponse.success("槽号 " + troughNo + " 失水率 " + record.getCurrentLossPct() + "%", record);
    }

    /** 下槽 */
    @PostMapping("/{troughNo}/release")
    public ApiResponse<WitheringRecord> release(@PathVariable String troughNo) {
        return ApiResponse.success("槽号 " + troughNo + " 已下槽，可承接下一批鲜叶",
                witheringService.release(troughNo));
    }
}
