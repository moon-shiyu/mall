package com.macro.mall;

import com.macro.mall.controller.PmsProductController;
import com.macro.mall.dto.PmsProductQueryParam;
import com.macro.mall.service.PmsProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 商品列表查询 — Controller层排序参数校验测试
 * 使用MockMvc验证排序白名单、非法参数拦截、原有筛选兼容性
 */
@WebMvcTest(PmsProductController.class)
@DisplayName("商品列表Controller排序校验测试")
public class PmsProductListTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PmsProductService productService;

    @Test
    @DisplayName("无排序参数时正常返回")
    void listWithoutSortParams_shouldSucceed() throws Exception {
        when(productService.list(any(PmsProductQueryParam.class), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(get("/product/list")
                        .param("pageSize", "5")
                        .param("pageNum", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @ParameterizedTest
    @ValueSource(strings = {"price", "sale", "stock", "newStatus", "id"})
    @DisplayName("合法排序字段均通过校验")
    void listWithValidSortField_shouldSucceed(String sortField) throws Exception {
        when(productService.list(any(PmsProductQueryParam.class), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(get("/product/list")
                        .param("sortField", sortField)
                        .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("非法排序字段返回validateFailed")
    void listWithInvalidSortField_shouldReturnValidateFailed() throws Exception {
        mockMvc.perform(get("/product/list")
                        .param("sortField", "name")
                        .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("排序字段不合法，允许的排序字段: price, sale, stock, newStatus, id"));
    }

    @Test
    @DisplayName("非法排序方向返回validateFailed")
    void listWithInvalidSortOrder_shouldReturnValidateFailed() throws Exception {
        mockMvc.perform(get("/product/list")
                        .param("sortField", "price")
                        .param("sortOrder", "random"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("排序方向不合法，允许的排序方向: asc, desc"));
    }

    @Test
    @DisplayName("排序字段合法但方向为空时默认desc，正常返回")
    void listWithSortFieldButNoOrder_shouldDefaultToDesc() throws Exception {
        when(productService.list(any(PmsProductQueryParam.class), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(get("/product/list")
                        .param("sortField", "price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("sortOrder大小写不敏感，ASC等同asc")
    void listWithUppercaseSortOrder_shouldSucceed() throws Exception {
        when(productService.list(any(PmsProductQueryParam.class), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(get("/product/list")
                        .param("sortField", "stock")
                        .param("sortOrder", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("SQL注入尝试 — sortField包含恶意内容被拒绝")
    void listWithSqlInjectionAttempt_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/product/list")
                        .param("sortField", "price; DROP TABLE pms_product;--")
                        .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("原有筛选参数与排序参数组合使用正常")
    void listWithExistingFiltersAndSort_shouldSucceed() throws Exception {
        when(productService.list(any(PmsProductQueryParam.class), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(get("/product/list")
                        .param("keyword", "手机")
                        .param("publishStatus", "1")
                        .param("brandId", "1")
                        .param("sortField", "sale")
                        .param("sortOrder", "desc")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("新增区间筛选参数正常传递")
    void listWithRangeFilters_shouldSucceed() throws Exception {
        when(productService.list(any(PmsProductQueryParam.class), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(get("/product/list")
                        .param("priceMin", "100")
                        .param("priceMax", "500")
                        .param("stockMin", "0")
                        .param("stockMax", "100")
                        .param("createTimeFrom", "2024-01-01")
                        .param("createTimeTo", "2024-12-31")
                        .param("sortField", "price")
                        .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("仅传sortOrder不传sortField时，不触发排序校验，正常返回")
    void listWithSortOrderOnly_shouldPassWithoutValidation() throws Exception {
        when(productService.list(any(PmsProductQueryParam.class), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(get("/product/list")
                        .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页+区间筛选+排序组合 — 第二页、每页3条")
    void listWithPaginationRangeFiltersAndSort_shouldSucceed() throws Exception {
        when(productService.list(any(PmsProductQueryParam.class), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(get("/product/list")
                        .param("pageNum", "2")
                        .param("pageSize", "3")
                        .param("priceMin", "50")
                        .param("priceMax", "1000")
                        .param("stockMin", "10")
                        .param("stockMax", "500")
                        .param("createTimeFrom", "2024-06-01")
                        .param("createTimeTo", "2025-12-31")
                        .param("keyword", "测试商品")
                        .param("publishStatus", "1")
                        .param("sortField", "stock")
                        .param("sortOrder", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页参数默认值 — 不传pageNum和pageSize时使用默认值")
    void listWithDefaultPagination_shouldSucceed() throws Exception {
        when(productService.list(any(PmsProductQueryParam.class), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        mockMvc.perform(get("/product/list")
                        .param("sortField", "id")
                        .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
