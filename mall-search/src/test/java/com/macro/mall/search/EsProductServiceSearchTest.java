package com.macro.mall.search;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.search.dao.EsProductDao;
import com.macro.mall.search.domain.EsProduct;
import com.macro.mall.search.domain.EsProductRelatedInfo;
import com.macro.mall.search.repository.EsProductRepository;
import com.macro.mall.search.service.impl.EsProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 商品搜索服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
public class EsProductServiceSearchTest {

    @Mock
    private ElasticsearchTemplate elasticsearchTemplate;

    @Mock
    private EsProductDao productDao;

    @Mock
    private EsProductRepository productRepository;

    @InjectMocks
    private EsProductServiceImpl esProductService;

    private void mockEmptySearchResult() {
        SearchHits<EsProduct> mockHits = mock(SearchHits.class);
        when(mockHits.getTotalHits()).thenReturn(0L);
        when(elasticsearchTemplate.search(any(), eq(EsProduct.class))).thenReturn(mockHits);
    }

    // ==================== 关键词搜索测试 ====================

    @Test
    public void testSearchEmptyKeyword() {
        mockEmptySearchResult();
        Page<EsProduct> result = esProductService.search(null, null, null,
                null, null, null, null, null, 0, 5, 0);
        assertEquals(0, result.getTotalElements());
        verify(elasticsearchTemplate).search(any(), eq(EsProduct.class));
    }

    @Test
    public void testSearchNonEmptyKeyword() {
        mockEmptySearchResult();
        esProductService.search("手机", null, null,
                null, null, null, null, null, 0, 5, 0);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        String dsl = captor.getValue().toString();
        assertTrue(dsl.contains("function_score") || dsl.contains("FunctionScore"),
                "非空关键词应使用 function_score 查询");
    }

    // ==================== 品牌/分类过滤测试 ====================

    @Test
    public void testSearchWithBrandAndCategory() {
        mockEmptySearchResult();
        esProductService.search(null, 1L, 2L,
                null, null, null, null, null, 0, 5, 0);
        verify(elasticsearchTemplate).search(any(), eq(EsProduct.class));
    }

    // ==================== 新增区间过滤测试 ====================

    @Test
    public void testSearchWithPublishStatus() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                1, null, null, null, null, 0, 5, 0);
        verify(elasticsearchTemplate).search(any(), eq(EsProduct.class));
    }

    @Test
    public void testSearchWithStockRange() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, 10, 100, null, null, 0, 5, 0);
        verify(elasticsearchTemplate).search(any(), eq(EsProduct.class));
    }

    @Test
    public void testSearchWithPriceRange() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, null,
                new BigDecimal("100"), new BigDecimal("500"),
                0, 5, 0);
        verify(elasticsearchTemplate).search(any(), eq(EsProduct.class));
    }

    @Test
    public void testSearchWithAllFilters() {
        mockEmptySearchResult();
        esProductService.search("手机", 1L, 2L,
                1, 0, 50,
                new BigDecimal("100"), new BigDecimal("999"),
                0, 5, 3);
        verify(elasticsearchTemplate).search(any(), eq(EsProduct.class));
    }

    // ==================== 参数校验测试 ====================

    @Test
    public void testSearchInvalidStockRange() {
        assertThrows(ApiException.class, () -> {
            esProductService.search(null, null, null,
                    null, 100, 10, null, null, 0, 5, 0);
        });
    }

    @Test
    public void testSearchInvalidPriceRange() {
        assertThrows(ApiException.class, () -> {
            esProductService.search(null, null, null,
                    null, null, null,
                    new BigDecimal("500"), new BigDecimal("100"),
                    0, 5, 0);
        });
    }

    @Test
    public void testSearchInvalidSortValue() {
        assertThrows(ApiException.class, () -> {
            esProductService.search(null, null, null,
                    null, null, null, null, null, 0, 5, 5);
        });
    }

    @Test
    public void testSearchNegativeSortValue() {
        assertThrows(ApiException.class, () -> {
            esProductService.search(null, null, null,
                    null, null, null, null, null, 0, 5, -1);
        });
    }

    @Test
    public void testSearchNegativeStock() {
        assertThrows(ApiException.class, () -> {
            esProductService.search(null, null, null,
                    null, -1, 100, null, null, 0, 5, 0);
        });
    }

    @Test
    public void testSearchNegativeMaxStock() {
        assertThrows(ApiException.class, () -> {
            esProductService.search(null, null, null,
                    null, null, -5, null, null, 0, 5, 0);
        });
    }

    @Test
    public void testSearchNegativePrice() {
        assertThrows(ApiException.class, () -> {
            esProductService.search(null, null, null,
                    null, null, null,
                    new BigDecimal("-100"), new BigDecimal("500"),
                    0, 5, 0);
        });
    }

    @Test
    public void testSearchNegativeMaxPrice() {
        assertThrows(ApiException.class, () -> {
            esProductService.search(null, null, null,
                    null, null, null,
                    null, new BigDecimal("-50"),
                    0, 5, 0);
        });
    }

    // ==================== 排序优先级测试 ====================

    @Test
    public void testSearchSortRelevance() {
        mockEmptySearchResult();
        esProductService.search("手机", null, null,
                null, null, null, null, null, 0, 5, 0);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        // sort=0 按相关度，DSL 中不应出现显式的 Sort 字段排序
        String dsl = captor.getValue().toString();
        assertFalse(dsl.contains("Sort{order="),
                "sort=0 不应添加显式字段排序");
    }

    @Test
    public void testSearchSortByNewest() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, null, null, null, 0, 5, 1);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        String dsl = captor.getValue().toString();
        assertTrue(dsl.contains("id"), "sort=1 应包含 id 排序");
    }

    @Test
    public void testSearchSortBySales() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, null, null, null, 0, 5, 2);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        String dsl = captor.getValue().toString();
        assertTrue(dsl.contains("sale"), "sort=2 应包含 sale 排序");
    }

    @Test
    public void testSearchSortByPriceAsc() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, null, null, null, 0, 5, 3);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        String dsl = captor.getValue().toString();
        assertTrue(dsl.contains("price"), "sort=3 应包含 price 排序");
    }

    @Test
    public void testSearchSortByPriceDesc() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, null, null, null, 0, 5, 4);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        String dsl = captor.getValue().toString();
        assertTrue(dsl.contains("price"), "sort=4 应包含 price 排序");
    }

    @Test
    public void testSearchSortByNewestWithScoreTiebreaker() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, null, null, null, 0, 5, 1);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        String dsl = captor.getValue().toString();
        assertTrue(dsl.contains("id"), "sort=1 应包含 id 排序");
        assertTrue(dsl.contains("_score"), "sort=1 应包含 _score 作为次级排序");
    }

    @Test
    public void testSearchSortBySalesWithScoreTiebreaker() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, null, null, null, 0, 5, 2);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        String dsl = captor.getValue().toString();
        assertTrue(dsl.contains("sale"), "sort=2 应包含 sale 排序");
        assertTrue(dsl.contains("_score"), "sort=2 应包含 _score 作为次级排序");
    }

    @Test
    public void testSearchSortByPriceAscWithScoreTiebreaker() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, null, null, null, 0, 5, 3);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        String dsl = captor.getValue().toString();
        assertTrue(dsl.contains("price"), "sort=3 应包含 price 排序");
        assertTrue(dsl.contains("_score"), "sort=3 应包含 _score 作为次级排序");
    }

    @Test
    public void testSearchSortByPriceDescWithScoreTiebreaker() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, null, null, null, 0, 5, 4);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        String dsl = captor.getValue().toString();
        assertTrue(dsl.contains("price"), "sort=4 应包含 price 排序");
        assertTrue(dsl.contains("_score"), "sort=4 应包含 _score 作为次级排序");
    }

    @Test
    public void testSearchSort0WithKeywordNoExplicitSort() {
        mockEmptySearchResult();
        esProductService.search("手机", null, null,
                null, null, null, null, null, 0, 5, 0);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        String dsl = captor.getValue().toString();
        assertFalse(dsl.contains("Sort{order="),
                "sort=0 即使有 keyword 也不应添加显式字段排序");
    }

    // ==================== 单边区间过滤测试 ====================

    @Test
    public void testSearchOnlyMinStock() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, 10, null, null, null, 0, 5, 0);
        verify(elasticsearchTemplate).search(any(), eq(EsProduct.class));
    }

    @Test
    public void testSearchOnlyMaxStock() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, 200, null, null, 0, 5, 0);
        verify(elasticsearchTemplate).search(any(), eq(EsProduct.class));
    }

    @Test
    public void testSearchOnlyMinPrice() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, null,
                new BigDecimal("50"), null,
                0, 5, 0);
        verify(elasticsearchTemplate).search(any(), eq(EsProduct.class));
    }

    @Test
    public void testSearchOnlyMaxPrice() {
        mockEmptySearchResult();
        esProductService.search(null, null, null,
                null, null, null,
                null, new BigDecimal("999"),
                0, 5, 0);
        verify(elasticsearchTemplate).search(any(), eq(EsProduct.class));
    }

    // ==================== 空关键词 + 过滤组合测试 ====================

    @Test
    public void testSearchEmptyKeywordWithAllFilters() {
        mockEmptySearchResult();
        esProductService.search(null, 1L, 2L,
                1, 0, 100,
                new BigDecimal("10"), new BigDecimal("500"),
                0, 5, 2);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        String dsl = captor.getValue().toString();
        assertTrue(dsl.contains("match_all") || dsl.contains("MatchAll"),
                "空关键词应使用 matchAll 查询");
        assertTrue(dsl.contains("sale"), "sort=2 应包含 sale 排序");
    }

    // ==================== searchRelatedInfo NPE 防护测试 ====================

    @Test
    public void testSearchRelatedInfoEmptyKeyword() {
        SearchHits<EsProduct> mockHits = mock(SearchHits.class);
        ElasticsearchAggregations mockAggs = mock(ElasticsearchAggregations.class);
        when(mockHits.getAggregations()).thenReturn(mockAggs);
        when(mockAggs.aggregationsAsMap()).thenReturn(Collections.emptyMap());
        when(elasticsearchTemplate.search(any(), eq(EsProduct.class))).thenReturn(mockHits);

        EsProductRelatedInfo result = esProductService.searchRelatedInfo(null);
        assertNotNull(result);
        assertNotNull(result.getBrandNames());
        assertNotNull(result.getProductCategoryNames());
        assertNotNull(result.getProductAttrs());
        assertTrue(result.getBrandNames().isEmpty());
        assertTrue(result.getProductCategoryNames().isEmpty());
        assertTrue(result.getProductAttrs().isEmpty());
    }

    @Test
    public void testSearchRelatedInfoNonEmptyKeyword() {
        SearchHits<EsProduct> mockHits = mock(SearchHits.class);
        ElasticsearchAggregations mockAggs = mock(ElasticsearchAggregations.class);
        when(mockHits.getAggregations()).thenReturn(mockAggs);
        when(mockAggs.aggregationsAsMap()).thenReturn(Collections.emptyMap());
        when(elasticsearchTemplate.search(any(), eq(EsProduct.class))).thenReturn(mockHits);

        EsProductRelatedInfo result = esProductService.searchRelatedInfo("手机");
        assertNotNull(result);
        assertNotNull(result.getBrandNames());
        assertTrue(result.getBrandNames().isEmpty());
    }

    @Test
    public void testSearchRelatedInfoNullAggregations() {
        SearchHits<EsProduct> mockHits = mock(SearchHits.class);
        when(mockHits.getAggregations()).thenReturn(null);
        when(elasticsearchTemplate.search(any(), eq(EsProduct.class))).thenReturn(mockHits);

        EsProductRelatedInfo result = esProductService.searchRelatedInfo(null);
        assertNotNull(result);
        assertNotNull(result.getBrandNames());
        assertNotNull(result.getProductCategoryNames());
        assertNotNull(result.getProductAttrs());
    }
}
