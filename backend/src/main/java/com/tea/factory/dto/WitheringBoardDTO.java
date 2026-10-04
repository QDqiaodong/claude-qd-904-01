package com.tea.factory.dto;

import java.util.List;

import com.tea.factory.domain.WitheringRecord;

import lombok.Data;

/**
 * 摊青看板：当前占用（摊青中 / 可杀青）与历史完成（已下槽履历）分开返回，
 * 页面和统计都只以 active 作为「当前槽数」口径，history 仅供追溯。
 */
@Data
public class WitheringBoardDTO {

    /** 当前占用：WITHERING / READY，每个槽号至多一条 */
    private List<WitheringRecord> active;

    /** 历史完成：DONE 履历，按槽号、轮次倒序 */
    private List<WitheringRecord> history;
}
