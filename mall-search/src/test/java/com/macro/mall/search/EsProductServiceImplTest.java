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
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EsProductServiceImpl 综合搜索增强的聚焦单测。
 * 通过 Mockito 抓取传入 ElasticsearchTemplate 的 NativeQuery 来验证查询/过滤/排序的构建逻辑，
 * 无需真实 Elasticsearch。
 */
@ExtendWith(MockitoExtension.class)
class EsProductServiceImplTest {

    @Mock
    private EsProductDao productDao;
    @Mock
    private EsProductRepository productRepository;
    @Mock
    private ElasticsearchTemplate elasticsearchTemplate;
    @InjectMocks
    private EsProductServiceImpl esProductService;

    @SuppressWarnings("unchecked")
    private void stubSearchReturnsEmpty() {
        SearchHits<EsProduct> emptyHits = mock(SearchHits.class);
        when(emptyHits.getTotalHits()).thenReturn(0L);
        when(elasticsearchTemplate.search(any(NativeQuery.class), eq(EsProduct.class))).thenReturn(emptyHits);
    }

    private NativeQuery captureSearchQuery() {
        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchTemplate).search(captor.capture(), eq(EsProduct.class));
        return captor.getValue();
    }

    @Test
    void emptyKeyword_usesMatchAll() {
        stubSearchReturnsEmpty();
        esProductService.search("", null, null, null, null, null, null, null, 0, 5, 0);
        NativeQuery query = captureSearchQuery();
        assertThat(query.getQuery().isMatchAll()).isTrue();
    }

    @Test
    void nonEmptyKeyword_usesFunctionScore() {
        stubSearchReturnsEmpty();
        esProductService.search("phone", null, null, null, null, null, null, null, 0, 5, 0);
        NativeQuery query = captureSearchQuery();
        assertThat(query.getQuery().isFunctionScore()).isTrue();
    }

    @Test
    void brandAndCategory_addTwoFilterClauses() {
        stubSearchReturnsEmpty();
        esProductService.search(null, 1L, 2L, null, null, null, null, null, 0, 5, 0);
        NativeQuery query = captureSearchQuery();
        assertThat(query.getFilter()).isNotNull();
        assertThat(query.getFilter().isBool()).isTrue();
        assertThat(query.getFilter().bool().filter()).hasSize(2);
    }

    @Test
    void publishStatusAndRanges_addAllFilterClauses() {
        stubSearchReturnsEmpty();
        esProductService.search(null, 1L, 2L, 1, 0, 100, new BigDecimal("10"), new BigDecimal("100"), 0, 5, 0);
        NativeQuery query = captureSearchQuery();
        // brandId + productCategoryId + publishStatus + stock区间 + price区间 = 5 个过滤子句
        assertThat(query.getFilter().bool().filter()).hasSize(5);
    }

    @Test
    void noFilters_noFilterApplied() {
        stubSearchReturnsEmpty();
        esProductService.search("phone", null, null, null, null, null, null, null, 0, 5, 0);
        NativeQuery query = captureSearchQuery();
        assertThat(query.getFilter()).isNull();
    }

    @Test
    void sortByPriceAsc_priceIsPrimary_scoreIsSecondary() {
        stubSearchReturnsEmpty();
        esProductService.search("phone", null, null, null, null, null, null, null, 0, 5, 3);
        NativeQuery query = captureSearchQuery();
        List<Sort.Order> orders = query.getSort().toList();
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getProperty()).isEqualTo("price");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(orders.get(1).getProperty()).isEqualTo("_score");
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void sortBySaleDesc_saleIsPrimary_scoreIsSecondary() {
        stubSearchReturnsEmpty();
        esProductService.search("phone", null, null, null, null, null, null, null, 0, 5, 2);
        NativeQuery query = captureSearchQuery();
        List<Sort.Order> orders = query.getSort().toList();
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getProperty()).isEqualTo("sale");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orders.get(1).getProperty()).isEqualTo("_score");
    }

    @Test
    void sortByRelevance_onlyScore() {
        stubSearchReturnsEmpty();
        esProductService.search("phone", null, null, null, null, null, null, null, 0, 5, 0);
        NativeQuery query = captureSearchQuery();
        List<Sort.Order> orders = query.getSort().toList();
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getProperty()).isEqualTo("_score");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void invalidPriceRange_throwsApiException_andDoesNotQueryEs() {
        assertThatThrownBy(() -> esProductService.search(null, null, null, null, null, null,
                new BigDecimal("100"), new BigDecimal("50"), 0, 5, 0))
                .isInstanceOf(ApiException.class);
        verify(elasticsearchTemplate, never()).search(any(NativeQuery.class), eq(EsProduct.class));
    }

    @Test
    void invalidStockRange_throwsApiException() {
        assertThatThrownBy(() -> esProductService.search(null, null, null, null, 10, 5, null, null, 0, 5, 0))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void negativePriceLowerBound_throwsApiException() {
        assertThatThrownBy(() -> esProductService.search(null, null, null, null, null, null,
                new BigDecimal("-1"), null, 0, 5, 0))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchRelatedInfo_nullAggregations_returnsEmpty_noNpe() {
        SearchHits<EsProduct> hits = mock(SearchHits.class);
        when(hits.getAggregations()).thenReturn(null);
        when(elasticsearchTemplate.search(any(NativeQuery.class), eq(EsProduct.class))).thenReturn(hits);
        EsProductRelatedInfo info = esProductService.searchRelatedInfo("phone");
        assertThat(info.getBrandNames()).isEmpty();
        assertThat(info.getProductCategoryNames()).isEmpty();
        assertThat(info.getProductAttrs()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchRelatedInfo_emptyKeyword_usesMatchAll() {
        SearchHits<EsProduct> hits = mock(SearchHits.class);
        when(elasticsearchTemplate.search(any(NativeQuery.class), eq(EsProduct.class))).thenReturn(hits);
        esProductService.searchRelatedInfo("");
        NativeQuery query = captureSearchQuery();
        assertThat(query.getQuery().isMatchAll()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchRelatedInfo_nonEmptyKeyword_usesMultiMatch() {
        SearchHits<EsProduct> hits = mock(SearchHits.class);
        when(elasticsearchTemplate.search(any(NativeQuery.class), eq(EsProduct.class))).thenReturn(hits);
        esProductService.searchRelatedInfo("phone");
        NativeQuery query = captureSearchQuery();
        assertThat(query.getQuery().isMultiMatch()).isTrue();
    }
}
