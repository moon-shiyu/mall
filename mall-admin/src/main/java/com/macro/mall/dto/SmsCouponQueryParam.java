package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 优惠券列表查询参数
 * Created by macro on 2018/8/28.
 */
@Getter
@Setter
public class SmsCouponQueryParam {
    @Schema(title = "优惠券名称（模糊匹配）")
    private String name;

    @Schema(title = "优惠券类型：0->全场赠券；1->会员赠券；2->购物赠券；3->注册赠券")
    private Integer type;

    @Schema(title = "优惠券状态：0->未开始；1->进行中；2->已结束；3->已停用")
    private Integer status;

    @Schema(title = "使用类型：0->全场通用；1->指定分类；2->指定商品")
    private Integer useType;

    @Schema(title = "有效期起始时间下限", format = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startTimeBegin;

    @Schema(title = "有效期起始时间上限", format = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startTimeEnd;

    @Schema(title = "有效期结束时间下限", format = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date endTimeBegin;

    @Schema(title = "有效期结束时间上限", format = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date endTimeEnd;

    @Schema(title = "领取数量最小值")
    private Integer receiveCountMin;

    @Schema(title = "领取数量最大值")
    private Integer receiveCountMax;

    @Schema(title = "剩余数量最小值")
    private Integer countMin;

    @Schema(title = "剩余数量最大值")
    private Integer countMax;
}
