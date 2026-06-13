package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品查询参数
 * Created by macro on 2018/4/27.
 */
@Data
@EqualsAndHashCode
public class PmsProductQueryParam {
    @Schema(title = "上架状态")
    private Integer publishStatus;
    @Schema(title = "审核状态")
    private Integer verifyStatus;
    @Schema(title = "商品名称模糊关键字")
    private String keyword;
    @Schema(title = "商品货号")
    private String productSn;
    @Schema(title = "商品分类编号")
    private Long productCategoryId;
    @Schema(title = "商品品牌编号")
    private Long brandId;
    @Schema(title = "价格下限")
    private BigDecimal priceMin;
    @Schema(title = "价格上限")
    private BigDecimal priceMax;
    @Schema(title = "库存下限")
    private Integer stockMin;
    @Schema(title = "库存上限")
    private Integer stockMax;
    @Schema(title = "创建时间起始")
    private Date createTimeFrom;
    @Schema(title = "创建时间截止")
    private Date createTimeTo;
    @Schema(title = "排序字段", description = "可选值: price, sale, stock, newStatus, id")
    private String sortField;
    @Schema(title = "排序方向", description = "可选值: asc, desc")
    private String sortOrder;
}
