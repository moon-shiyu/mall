package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

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
    @Schema(title = "价格区间下限")
    private BigDecimal minPrice;
    @Schema(title = "价格区间上限")
    private BigDecimal maxPrice;
    @Schema(title = "库存区间下限")
    private Integer minStock;
    @Schema(title = "库存区间上限")
    private Integer maxStock;
    @Schema(title = "创建时间范围起始，格式 yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTimeStart;
    @Schema(title = "创建时间范围结束，格式 yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTimeEnd;
    @Schema(title = "排序字段，仅支持：price/sale/stock/newStatus")
    private String sortField;
    @Schema(title = "排序方向：asc/desc，缺省asc")
    private String sortOrder;
}
