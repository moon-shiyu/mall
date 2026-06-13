package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 订单查询参数
 * Created by macro on 2018/10/11.
 */
@Getter
@Setter
public class OmsOrderQueryParam {
    @Schema(title =  "订单编号")
    private String orderSn;
    @Schema(title =  "收货人姓名/号码")
    private String receiverKeyword;
    @Schema(title =  "订单状态：0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭；5->无效订单")
    private Integer status;
    @Schema(title =  "订单状态集合（多选筛选），与status互斥，优先使用statusList")
    private List<Integer> statusList;
    @Schema(title =  "订单类型：0->正常订单；1->秒杀订单")
    private Integer orderType;
    @Schema(title =  "订单来源：0->PC订单；1->app订单")
    private Integer sourceType;
    @Schema(title =  "订单提交时间（按日期前缀模糊匹配，兼容旧逻辑）")
    private String createTime;
    @Schema(title =  "下单时间范围-起始（格式：yyyy-MM-dd HH:mm:ss）")
    private String createTimeFrom;
    @Schema(title =  "下单时间范围-截止（格式：yyyy-MM-dd HH:mm:ss）")
    private String createTimeTo;
    @Schema(title =  "收货人手机号（精确匹配）")
    private String receiverPhone;
    @Schema(title =  "支付方式：0->未支付；1->支付宝；2->微信")
    private Integer payType;
}
