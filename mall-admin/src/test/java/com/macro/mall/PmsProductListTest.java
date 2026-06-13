package com.macro.mall;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.dto.PmsProductQueryParam;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.service.impl.PmsProductServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品列表查询（筛选/区间/排序/分页）聚焦单元测试。
 * 纯 Mockito，不依赖数据库与 Spring 上下文：mock {@link PmsProductMapper}，
 * 用 ArgumentCaptor 捕获 service 构建出的 {@link PmsProductExample}，断言其条件与排序子句。
 */
@ExtendWith(MockitoExtension.class)
public class PmsProductListTest {

    @Mock
    private PmsProductMapper productMapper;

    @InjectMocks
    private PmsProductServiceImpl productService;

    @AfterEach
    void clearPageHelper() {
        // selectByExample 被 mock，未经过 PageHelper 拦截器，需手动清理线程局部分页参数，避免泄漏
        PageHelper.clearPage();
    }

    // ---------- 工具方法 ----------

    private PmsProductExample runAndCapture(PmsProductQueryParam param) {
        when(productMapper.selectByExample(any(PmsProductExample.class))).thenReturn(Collections.emptyList());
        productService.list(param, 5, 1);
        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        return captor.getValue();
    }

    private static List<PmsProductExample.Criterion> allCriteria(PmsProductExample example) {
        List<PmsProductExample.Criterion> result = new ArrayList<>();
        for (PmsProductExample.Criteria c : example.getOredCriteria()) {
            result.addAll(c.getAllCriteria());
        }
        return result;
    }

    private static boolean hasCondition(PmsProductExample example, String condition) {
        return allCriteria(example).stream().anyMatch(cr -> condition.equals(cr.getCondition()));
    }

    private static boolean hasCondition(PmsProductExample example, String condition, Object value) {
        return allCriteria(example).stream()
                .anyMatch(cr -> condition.equals(cr.getCondition()) && Objects.equals(value, cr.getValue()));
    }

    // ---------- 原有筛选条件 ----------

    @Test
    void originalFiltersAreApplied() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setPublishStatus(1);
        param.setVerifyStatus(1);
        param.setKeyword("shirt");
        param.setProductSn("SN123");
        param.setBrandId(5L);
        param.setProductCategoryId(7L);

        PmsProductExample example = runAndCapture(param);

        assertTrue(hasCondition(example, "delete_status =", 0), "应始终带 delete_status=0");
        assertTrue(hasCondition(example, "publish_status =", 1));
        assertTrue(hasCondition(example, "verify_status =", 1));
        assertTrue(hasCondition(example, "name like", "%shirt%"));
        assertTrue(hasCondition(example, "product_sn =", "SN123"));
        assertTrue(hasCondition(example, "brand_id =", 5L));
        assertTrue(hasCondition(example, "product_category_id =", 7L));
        assertNull(example.getOrderByClause(), "未传排序字段时不应有 order by");
    }

    @Test
    void emptyParamOnlyKeepsDeleteStatus() {
        PmsProductExample example = runAndCapture(new PmsProductQueryParam());
        assertTrue(hasCondition(example, "delete_status =", 0));
        // 仅保留 delete_status，一条条件
        assertEquals(1, allCriteria(example).size());
        assertNull(example.getOrderByClause());
    }

    // ---------- 价格区间 ----------

    @Test
    void priceRangeBothBounds() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setMinPrice(new BigDecimal("10"));
        param.setMaxPrice(new BigDecimal("100"));

        PmsProductExample example = runAndCapture(param);

        assertTrue(hasCondition(example, "price >=", new BigDecimal("10")));
        assertTrue(hasCondition(example, "price <=", new BigDecimal("100")));
    }

    @Test
    void priceRangeOnlyMin() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setMinPrice(new BigDecimal("10"));

        PmsProductExample example = runAndCapture(param);

        assertTrue(hasCondition(example, "price >="));
        assertFalse(hasCondition(example, "price <="));
    }

    @Test
    void priceRangeOnlyMax() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setMaxPrice(new BigDecimal("100"));

        PmsProductExample example = runAndCapture(param);

        assertFalse(hasCondition(example, "price >="));
        assertTrue(hasCondition(example, "price <="));
    }

    @Test
    void invertedPriceRangeIsStableNoException() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setMinPrice(new BigDecimal("100"));
        param.setMaxPrice(new BigDecimal("10"));

        // 不应抛异常；两条件并存，由数据库返回空集，行为稳定
        PmsProductExample example = runAndCapture(param);

        assertTrue(hasCondition(example, "price >=", new BigDecimal("100")));
        assertTrue(hasCondition(example, "price <=", new BigDecimal("10")));
    }

    // ---------- 库存区间 ----------

    @Test
    void stockRangeBothBounds() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setMinStock(5);
        param.setMaxStock(50);

        PmsProductExample example = runAndCapture(param);

        assertTrue(hasCondition(example, "stock >=", 5));
        assertTrue(hasCondition(example, "stock <=", 50));
    }

    @Test
    void stockRangeOnlyMin() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setMinStock(5);

        PmsProductExample example = runAndCapture(param);

        assertTrue(hasCondition(example, "stock >="));
        assertFalse(hasCondition(example, "stock <="));
    }

    // ---------- 创建时间范围 ----------

    @Test
    void createTimeRangeBothBounds() {
        Date start = new Date(1_700_000_000_000L);
        Date end = new Date(1_800_000_000_000L);
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setCreateTimeStart(start);
        param.setCreateTimeEnd(end);

        PmsProductExample example = runAndCapture(param);

        assertTrue(hasCondition(example, "create_time >=", start));
        assertTrue(hasCondition(example, "create_time <=", end));
    }

    @Test
    void createTimeRangeOnlyStart() {
        Date start = new Date(1_700_000_000_000L);
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setCreateTimeStart(start);

        PmsProductExample example = runAndCapture(param);

        assertTrue(hasCondition(example, "create_time >=", start));
        assertFalse(hasCondition(example, "create_time <="));
    }

    // ---------- 排序（白名单） ----------

    @Test
    void validSortPriceDesc() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortField("price");
        param.setSortOrder("desc");

        assertEquals("price desc, id asc", runAndCapture(param).getOrderByClause());
    }

    @Test
    void validSortNewStatusDefaultsToAsc() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortField("newStatus");

        // newStatus 映射到列 new_status；缺省方向 asc；追加 id asc 稳定排序
        assertEquals("new_status asc, id asc", runAndCapture(param).getOrderByClause());
    }

    @Test
    void validSortStockDirectionIsCaseInsensitive() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortField("stock");
        param.setSortOrder("DESC");

        assertEquals("stock desc, id asc", runAndCapture(param).getOrderByClause());
    }

    @Test
    void validSortSaleAsc() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortField("sale");
        param.setSortOrder("asc");

        assertEquals("sale asc, id asc", runAndCapture(param).getOrderByClause());
    }

    @Test
    void blankSortFieldMeansNoOrderBy() {
        PmsProductExample example = runAndCapture(new PmsProductQueryParam());
        assertNull(example.getOrderByClause());
    }

    @Test
    void sortOrderWithoutFieldIsIgnored() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortOrder("desc"); // 没有 sortField

        assertNull(runAndCapture(param).getOrderByClause());
    }

    // ---------- 非法排序参数（应返回清晰错误，防注入） ----------

    @Test
    void illegalSortFieldThrows() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortField("name"); // 不在白名单

        assertThrows(ApiException.class, () -> productService.list(param, 5, 1));
    }

    @Test
    void sqlInjectionSortFieldThrows() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortField("id; drop table pms_product"); // 注入尝试

        assertThrows(ApiException.class, () -> productService.list(param, 5, 1));
    }

    @Test
    void illegalSortDirectionThrows() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortField("price");
        param.setSortOrder("ascending"); // 非 asc/desc

        assertThrows(ApiException.class, () -> productService.list(param, 5, 1));
    }

    // ---------- 分页装配 ----------

    @Test
    void paginationIsWiredToPageHelper() {
        when(productMapper.selectByExample(any(PmsProductExample.class))).thenReturn(Collections.emptyList());

        productService.list(new PmsProductQueryParam(), 7, 3);

        Page<?> localPage = PageHelper.getLocalPage();
        assertNotNull(localPage, "list() 应通过 PageHelper.startPage 设置分页");
        assertEquals(3, localPage.getPageNum());
        assertEquals(7, localPage.getPageSize());
    }

    // ---------- 组合：筛选 + 区间 + 排序 ----------

    @Test
    void combinedFiltersRangesAndSort() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setKeyword("phone");
        param.setPublishStatus(1);
        param.setMinPrice(new BigDecimal("100"));
        param.setMaxStock(1000);
        param.setSortField("sale");
        param.setSortOrder("desc");

        PmsProductExample example = runAndCapture(param);

        assertTrue(hasCondition(example, "delete_status =", 0));
        assertTrue(hasCondition(example, "name like", "%phone%"));
        assertTrue(hasCondition(example, "publish_status =", 1));
        assertTrue(hasCondition(example, "price >=", new BigDecimal("100")));
        assertTrue(hasCondition(example, "stock <=", 1000));
        assertEquals("sale desc, id asc", example.getOrderByClause());
    }
}
