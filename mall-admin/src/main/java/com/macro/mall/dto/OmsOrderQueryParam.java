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
    @Schema(title =  "订单类型：0->正常订单；1->秒杀订单")
    private Integer orderType;
    @Schema(title =  "订单来源：0->PC订单；1->app订单")
    private Integer sourceType;
    @Schema(title =  "订单提交时间")
    private String createTime;
    @Schema(title =  "支付方式：0->未支付；1->支付宝；2->微信")
    private Integer payType;
    @Schema(title =  "收货人手机号")
    private String receiverPhone;
    @Schema(title =  "订单状态集合：0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭；5->无效订单")
    private List<Integer> statusList;
    @Schema(title =  "下单开始时间，格式：yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String createTimeStart;
    @Schema(title =  "下单结束时间，格式：yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String createTimeEnd;
}
