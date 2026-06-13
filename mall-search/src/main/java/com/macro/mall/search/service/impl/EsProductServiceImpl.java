package com.macro.mall.search.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.ObjectBuilder;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.search.dao.EsProductDao;
import com.macro.mall.search.domain.EsProduct;
import com.macro.mall.search.domain.EsProductRelatedInfo;
import com.macro.mall.search.repository.EsProductRepository;
import com.macro.mall.search.service.EsProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.*;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 搜索商品管理Service实现类
 * Created by macro on 2018/6/19.
 */
@Service
public class EsProductServiceImpl implements EsProductService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EsProductServiceImpl.class);
    @Autowired
    private EsProductDao productDao;
    @Autowired
    private EsProductRepository productRepository;
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;
    @Override
    public int importAll() {
        List<EsProduct> esProductList = productDao.getAllEsProductList(null);
        Iterable<EsProduct> esProductIterable = productRepository.saveAll(esProductList);
        Iterator<EsProduct> iterator = esProductIterable.iterator();
        int result = 0;
        while (iterator.hasNext()) {
            result++;
            iterator.next();
        }
        return result;
    }

    @Override
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    @Override
    public EsProduct create(Long id) {
        EsProduct result = null;
        List<EsProduct> esProductList = productDao.getAllEsProductList(id);
        if (esProductList.size() > 0) {
            EsProduct esProduct = esProductList.get(0);
            result = productRepository.save(esProduct);
        }
        return result;
    }

    @Override
    public void delete(List<Long> ids) {
        if (!CollectionUtils.isEmpty(ids)) {
            List<EsProduct> esProductList = new ArrayList<>();
            for (Long id : ids) {
                EsProduct esProduct = new EsProduct();
                esProduct.setId(id);
                esProductList.add(esProduct);
            }
            productRepository.deleteAll(esProductList);
        }
    }

    @Override
    public Page<EsProduct> search(String keyword, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        return productRepository.findByNameOrSubTitleOrKeywords(keyword, keyword, keyword, pageable);
    }

    @Override
    public Page<EsProduct> search(String keyword, Long brandId, Long productCategoryId,
                                  Integer publishStatus,
                                  Integer minStock, Integer maxStock,
                                  BigDecimal minPrice, BigDecimal maxPrice,
                                  Integer pageNum, Integer pageSize, Integer sort) {
        //参数校验
        if (minStock != null && maxStock != null && minStock > maxStock) {
            Asserts.fail("最小库存不能大于最大库存");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            Asserts.fail("最低价格不能大于最高价格");
        }
        if ((minStock != null && minStock < 0) || (maxStock != null && maxStock < 0)) {
            Asserts.fail("库存不能为负数");
        }
        if ((minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) ||
                (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0)) {
            Asserts.fail("价格不能为负数");
        }
        if (sort != null && (sort < 0 || sort > 4)) {
            Asserts.fail("排序参数不合法，取值范围：0-4");
        }
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        NativeQueryBuilder nativeQueryBuilder = new NativeQueryBuilder();
        //分页
        nativeQueryBuilder.withPageable(pageable);
        //过滤
        List<Query> filterClauses = new ArrayList<>();
        if (brandId != null) {
            filterClauses.add(QueryBuilders.term(b -> b.field("brandId").value(brandId)));
        }
        if (productCategoryId != null) {
            filterClauses.add(QueryBuilders.term(b -> b.field("productCategoryId").value(productCategoryId)));
        }
        if (publishStatus != null) {
            filterClauses.add(QueryBuilders.term(b -> b.field("publishStatus").value(publishStatus)));
        }
        if (minStock != null || maxStock != null) {
            filterClauses.add(QueryBuilders.range(b -> {
                b.field("stock");
                if (minStock != null) b.gte(JsonData.of(minStock));
                if (maxStock != null) b.lte(JsonData.of(maxStock));
                return b;
            }));
        }
        if (minPrice != null || maxPrice != null) {
            filterClauses.add(QueryBuilders.range(b -> {
                b.field("price");
                if (minPrice != null) b.gte(JsonData.of(minPrice));
                if (maxPrice != null) b.lte(JsonData.of(maxPrice));
                return b;
            }));
        }
        if (!filterClauses.isEmpty()) {
            nativeQueryBuilder.withFilter(QueryBuilders.bool(b -> b.must(filterClauses)));
        }
        //搜索
        if (StrUtil.isEmpty(keyword)) {
            nativeQueryBuilder.withQuery(QueryBuilders.matchAll(builder -> builder));
        } else {
            List<FunctionScore> functionScoreList = new ArrayList<>();
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("name").query(keyword)))
                    .weight(10.0)
                    .build());
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("subTitle").query(keyword)))
                    .weight(5.0)
                    .build());
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("keywords").query(keyword)))
                    .weight(2.0)
                    .build());
            FunctionScoreQuery.Builder functionScoreQueryBuilder = QueryBuilders.functionScore()
                    .functions(functionScoreList)
                    .scoreMode(FunctionScoreMode.Sum)
                    .minScore(2.0);
            nativeQueryBuilder.withQuery(builder -> builder.functionScore(functionScoreQueryBuilder.build()));
        }
        //排序：sort=0 仅按相关度；sort=1..4 按业务字段为主排序，_score 为次级排序
        if (sort != null && sort >= 1 && sort <= 4) {
            switch (sort) {
                case 1 -> nativeQueryBuilder.withSort(Sort.by(Sort.Order.desc("id")));
                case 2 -> nativeQueryBuilder.withSort(Sort.by(Sort.Order.desc("sale")));
                case 3 -> nativeQueryBuilder.withSort(Sort.by(Sort.Order.asc("price")));
                case 4 -> nativeQueryBuilder.withSort(Sort.by(Sort.Order.desc("price")));
            }
            //业务字段排序时，_score 作为次级排序（tiebreaker），不覆盖主排序
            nativeQueryBuilder.withSort(Sort.by(Sort.Order.desc("_score")));
        }
        //sort=0 或 null 时不显式追加 _score，ES 默认按 _score 排序
        NativeQuery nativeQuery = nativeQueryBuilder.build();
        LOGGER.info("DSL:{}", nativeQuery.getQuery().toString());
        SearchHits<EsProduct> searchHits = elasticsearchTemplate.search(nativeQuery, EsProduct.class);
        if(searchHits.getTotalHits()<=0){
            return new PageImpl<>(ListUtil.empty(),pageable,0);
        }
        List<EsProduct> searchProductList = searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList());
        return new PageImpl<>(searchProductList,pageable,searchHits.getTotalHits());
    }

    @Override
    public Page<EsProduct> recommend(Long id, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        List<EsProduct> esProductList = productDao.getAllEsProductList(id);
        if (esProductList.size() > 0) {
            EsProduct esProduct = esProductList.get(0);
            String keyword = esProduct.getName();
            Long brandId = esProduct.getBrandId();
            Long productCategoryId = esProduct.getProductCategoryId();
            //构建查询条件
            NativeQueryBuilder nativeQueryBuilder = new NativeQueryBuilder();
            //分页
            nativeQueryBuilder.withPageable(pageable);
            //用于过滤掉相同的商品
            nativeQueryBuilder.withFilter(QueryBuilders.bool(build -> build.mustNot(QueryBuilders.term(b->b.field("id").value(id)))));
            //根据商品标题、品牌、分类进行搜索
            List<FunctionScore> functionScoreList = new ArrayList<>();
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("name").query(keyword)))
                    .weight(8.0)
                    .build());
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("subTitle").query(keyword)))
                    .weight(2.0)
                    .build());
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("keywords").query(keyword)))
                    .weight(2.0)
                    .build());
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("brandId").query(brandId)))
                    .weight(5.0)
                    .build());
            functionScoreList.add(new FunctionScore.Builder()
                    .filter(QueryBuilders.match(builder -> builder.field("productCategoryId").query(productCategoryId)))
                    .weight(3.0)
                    .build());
            FunctionScoreQuery.Builder functionScoreQueryBuilder = QueryBuilders.functionScore()
                    .functions(functionScoreList)
                    .scoreMode(FunctionScoreMode.Sum)
                    .minScore(2.0);
            nativeQueryBuilder.withQuery(builder -> builder.functionScore(functionScoreQueryBuilder.build()));
            NativeQuery nativeQuery = nativeQueryBuilder.build();
            LOGGER.info("DSL:{}", nativeQuery.getQuery().toString());
            SearchHits<EsProduct> searchHits = elasticsearchTemplate.search(nativeQuery, EsProduct.class);
            if(searchHits.getTotalHits()<=0){
                return new PageImpl<>(ListUtil.empty(),pageable,0);
            }
            List<EsProduct> searchProductList = searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList());
            return new PageImpl<>(searchProductList,pageable,searchHits.getTotalHits());
        }
        return new PageImpl<>(ListUtil.empty());
    }

    @Override
    public EsProductRelatedInfo searchRelatedInfo(String keyword) {
        NativeQueryBuilder nativeQueryBuilder = new NativeQueryBuilder();
        //搜索条件
        if(StrUtil.isEmpty(keyword)){
            nativeQueryBuilder.withQuery(QueryBuilders.matchAll(builder -> builder));
        }else{
            nativeQueryBuilder.withQuery(QueryBuilders.multiMatch(builder -> builder.fields("name","subTitle","keywords").query(keyword)));
        }
        //聚合搜索品牌名称
        nativeQueryBuilder.withAggregation("brandNames",AggregationBuilders.terms(builder -> builder.field("brandName").size(10)));
        //聚合搜索分类名称
        nativeQueryBuilder.withAggregation("productCategoryNames",AggregationBuilders.terms(builder -> builder.field("productCategoryName").size(10)));
        //聚合搜索商品属性，去除type=0的属性
        Aggregation aggregation = new Aggregation.Builder().nested(builder -> builder.path("attrValueList"))
                .aggregations("productAttrs",new Aggregation.Builder()
                        .filter(b->b.term(a->a.field("attrValueList.type").value("1")))
                        .aggregations("attrIds",new Aggregation.Builder().terms(b->b.field("attrValueList.productAttributeId").size(10))
                                .aggregations("attrValues",new Aggregation.Builder().terms(b->b.field("attrValueList.value").size(10)).build())
                                .aggregations("attrNames",new Aggregation.Builder().terms(b->b.field("attrValueList.name").size(10)).build())
                                .build()).build()).build();
        nativeQueryBuilder.withAggregation("allAttrValues",aggregation);
        NativeQuery nativeQuery = nativeQueryBuilder.build();
        LOGGER.info("DSL:{}", nativeQueryBuilder.getQuery().toString());
        SearchHits<EsProduct> searchHits = elasticsearchTemplate.search(nativeQuery, EsProduct.class);
        return convertProductRelatedInfo(searchHits);
    }

    /**
     * 将返回结果转换为对象
     */
    private EsProductRelatedInfo convertProductRelatedInfo(SearchHits<EsProduct> response) {
        EsProductRelatedInfo productRelatedInfo = new EsProductRelatedInfo();
        if (response == null || response.getAggregations() == null) {
            productRelatedInfo.setBrandNames(Collections.emptyList());
            productRelatedInfo.setProductCategoryNames(Collections.emptyList());
            productRelatedInfo.setProductAttrs(Collections.emptyList());
            return productRelatedInfo;
        }
        Map<String, ElasticsearchAggregation> esAggregationMap =
                ((ElasticsearchAggregations) response.getAggregations()).aggregationsAsMap();
        //设置品牌
        productRelatedInfo.setBrandNames(extractStringTermsKeys(esAggregationMap, "brandNames"));
        //设置分类
        productRelatedInfo.setProductCategoryNames(extractStringTermsKeys(esAggregationMap, "productCategoryNames"));
        //设置参数
        productRelatedInfo.setProductAttrs(extractProductAttrs(esAggregationMap));
        return productRelatedInfo;
    }

    /**
     * 安全提取 StringTerms 聚合的 key 列表
     */
    private List<String> extractStringTermsKeys(Map<String, ElasticsearchAggregation> aggMap, String aggName) {
        ElasticsearchAggregation agg = aggMap.get(aggName);
        if (agg == null) {
            return Collections.emptyList();
        }
        Aggregate<?, ?> aggregate = agg.aggregation().getAggregate();
        if (aggregate._get() instanceof StringTermsAggregate termsAggregate) {
            List<StringTermsBucket> buckets = termsAggregate.buckets().array();
            List<String> keys = new ArrayList<>();
            for (StringTermsBucket bucket : buckets) {
                keys.add(bucket.key().stringValue());
            }
            return keys;
        }
        return Collections.emptyList();
    }

    /**
     * 安全提取嵌套属性聚合结果
     */
    private List<EsProductRelatedInfo.ProductAttr> extractProductAttrs(Map<String, ElasticsearchAggregation> aggMap) {
        ElasticsearchAggregation productAttrsAgg = aggMap.get("allAttrValues");
        if (productAttrsAgg == null) {
            return Collections.emptyList();
        }
        Aggregate<?, ?> rootAggregate = productAttrsAgg.aggregation().getAggregate();
        if (!(rootAggregate._get() instanceof NestedAggregate nestedAggregate)) {
            return Collections.emptyList();
        }
        Aggregate<?, ?> productAttrsFilter = nestedAggregate.aggregations().get("productAttrs");
        if (productAttrsFilter == null || !(productAttrsFilter._get() instanceof FilterAggregate filterAggregate)) {
            return Collections.emptyList();
        }
        Aggregate<?, ?> attrIdsAgg = filterAggregate.aggregations().get("attrIds");
        if (attrIdsAgg == null || !(attrIdsAgg._get() instanceof LongTermsAggregate longTermsAggregate)) {
            return Collections.emptyList();
        }
        List<LongTermsBucket> attrIdBuckets = longTermsAggregate.buckets().array();
        List<EsProductRelatedInfo.ProductAttr> attrList = new ArrayList<>();
        for (LongTermsBucket item : attrIdBuckets) {
            EsProductRelatedInfo.ProductAttr attr = new EsProductRelatedInfo.ProductAttr();
            attr.setAttrId(item.key());
            //提取属性值
            Aggregate<?, ?> attrValuesAgg = item.aggregations().get("attrValues");
            List<String> attrValueList = new ArrayList<>();
            if (attrValuesAgg != null && attrValuesAgg._get() instanceof StringTermsAggregate valuesTerms) {
                for (StringTermsBucket attrValue : valuesTerms.buckets().array()) {
                    attrValueList.add(attrValue.key().stringValue());
                }
            }
            attr.setAttrValues(attrValueList);
            //提取属性名
            Aggregate<?, ?> attrNamesAgg = item.aggregations().get("attrNames");
            if (attrNamesAgg != null && attrNamesAgg._get() instanceof StringTermsAggregate namesTerms) {
                List<StringTermsBucket> attrNames = namesTerms.buckets().array();
                if (!CollectionUtils.isEmpty(attrNames)) {
                    attr.setAttrName(attrNames.get(0).key().stringValue());
                }
            }
            attrList.add(attr);
        }
        return attrList;
    }
}
