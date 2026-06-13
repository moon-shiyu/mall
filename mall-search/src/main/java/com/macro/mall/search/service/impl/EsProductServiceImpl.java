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
                                  Integer publishStatus, Integer stockMin, Integer stockMax,
                                  BigDecimal priceMin, BigDecimal priceMax,
                                  Integer pageNum, Integer pageSize, Integer sort) {
        //区间参数校验，非法时返回清晰错误（由GlobalExceptionHandler统一转换为CommonResult.failed）
        if (stockMin != null && stockMin < 0) {
            Asserts.fail("库存下限不能为负数");
        }
        if (stockMin != null && stockMax != null && stockMin > stockMax) {
            Asserts.fail("库存区间不合法：stockMin 不能大于 stockMax");
        }
        if (priceMin != null && priceMin.compareTo(BigDecimal.ZERO) < 0) {
            Asserts.fail("价格下限不能为负数");
        }
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            Asserts.fail("价格区间不合法：priceMin 不能大于 priceMax");
        }
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        NativeQueryBuilder nativeQueryBuilder = new NativeQueryBuilder();
        //分页
        nativeQueryBuilder.withPageable(pageable);
        //过滤：品牌、分类、上架状态、库存区间、价格区间合并到一个 bool.filter，过滤不影响相关度打分
        List<Query> filterList = new ArrayList<>();
        if (brandId != null) {
            filterList.add(QueryBuilders.term(b -> b.field("brandId").value(brandId)));
        }
        if (productCategoryId != null) {
            filterList.add(QueryBuilders.term(b -> b.field("productCategoryId").value(productCategoryId)));
        }
        if (publishStatus != null) {
            filterList.add(QueryBuilders.term(b -> b.field("publishStatus").value(publishStatus.longValue())));
        }
        if (stockMin != null || stockMax != null) {
            filterList.add(QueryBuilders.range(r -> r.untyped(u -> {
                u.field("stock");
                if (stockMin != null) {
                    u.gte(JsonData.of(stockMin));
                }
                if (stockMax != null) {
                    u.lte(JsonData.of(stockMax));
                }
                return u;
            })));
        }
        if (priceMin != null || priceMax != null) {
            filterList.add(QueryBuilders.range(r -> r.untyped(u -> {
                u.field("price");
                if (priceMin != null) {
                    u.gte(JsonData.of(priceMin));
                }
                if (priceMax != null) {
                    u.lte(JsonData.of(priceMax));
                }
                return u;
            })));
        }
        if (!filterList.isEmpty()) {
            nativeQueryBuilder.withFilter(QueryBuilders.bool(b -> b.filter(filterList)));
        }
        //搜索：空关键词使用 matchAll；非空关键词按 name/subTitle/keywords 加权
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
        //排序：明确区分相关度排序(sort=0)与业务字段排序(sort=1..4)
        int sortType = sort == null ? 0 : sort;
        Sort businessSort = null;
        switch (sortType) {
            case 1:
                //按新品从新到旧
                businessSort = Sort.by(Sort.Order.desc("id"));
                break;
            case 2:
                //按销量从高到低
                businessSort = Sort.by(Sort.Order.desc("sale"));
                break;
            case 3:
                //按价格从低到高
                businessSort = Sort.by(Sort.Order.asc("price"));
                break;
            case 4:
                //按价格从高到低
                businessSort = Sort.by(Sort.Order.desc("price"));
                break;
            default:
                //sort=0 或其它取值：按相关度
                break;
        }
        if (businessSort != null) {
            //业务字段为主排序，_score 仅作次级排序（tie-breaker），不覆盖主排序
            nativeQueryBuilder.withSort(businessSort);
            nativeQueryBuilder.withSort(Sort.by(Sort.Order.desc("_score")));
        } else {
            //按相关度排序
            nativeQueryBuilder.withSort(Sort.by(Sort.Order.desc("_score")));
        }
        NativeQuery nativeQuery = nativeQueryBuilder.build();
        LOGGER.info("DSL:{}", nativeQuery.getQuery().toString());
        SearchHits<EsProduct> searchHits = elasticsearchTemplate.search(nativeQuery, EsProduct.class);
        if (searchHits.getTotalHits() <= 0) {
            return new PageImpl<>(ListUtil.empty(), pageable, 0);
        }
        List<EsProduct> searchProductList = searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList());
        return new PageImpl<>(searchProductList, pageable, searchHits.getTotalHits());
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
        List<String> brandNameList = new ArrayList<>();
        List<String> productCategoryNameList = new ArrayList<>();
        List<EsProductRelatedInfo.ProductAttr> attrList = new ArrayList<>();
        productRelatedInfo.setBrandNames(brandNameList);
        productRelatedInfo.setProductCategoryNames(productCategoryNameList);
        productRelatedInfo.setProductAttrs(attrList);
        //无聚合结果（无命中或未返回聚合）时直接返回空集合，避免空指针
        if (!(response.getAggregations() instanceof ElasticsearchAggregations esAggregations)) {
            return productRelatedInfo;
        }
        Map<String, ElasticsearchAggregation> esAggregationMap = esAggregations.aggregationsAsMap();
        //设置品牌
        ElasticsearchAggregation brandNames = esAggregationMap.get("brandNames");
        if (brandNames != null) {
            List<StringTermsBucket> brandNameBuckets = ((StringTermsAggregate) brandNames.aggregation().getAggregate()._get()).buckets().array();
            for (StringTermsBucket bucket : brandNameBuckets) {
                brandNameList.add(bucket.key().stringValue());
            }
        }
        //设置分类
        ElasticsearchAggregation productCategoryNames = esAggregationMap.get("productCategoryNames");
        if (productCategoryNames != null) {
            List<StringTermsBucket> productCategoryNameBuckets = ((StringTermsAggregate) productCategoryNames.aggregation().getAggregate()._get()).buckets().array();
            for (StringTermsBucket bucket : productCategoryNameBuckets) {
                productCategoryNameList.add(bucket.key().stringValue());
            }
        }
        //设置参数（去除type=0的属性后聚合），逐层判空避免空指针
        ElasticsearchAggregation productAttrs = esAggregationMap.get("allAttrValues");
        if (productAttrs != null) {
            NestedAggregate nestedAggregate = (NestedAggregate) productAttrs.aggregation().getAggregate()._get();
            Aggregate productAttrsAggregate = nestedAggregate.aggregations().get("productAttrs");
            if (productAttrsAggregate != null) {
                Aggregate attrIdsAggregate = ((FilterAggregate) productAttrsAggregate._get()).aggregations().get("attrIds");
                if (attrIdsAggregate != null) {
                    List<LongTermsBucket> attrIdBuckets = ((LongTermsAggregate) attrIdsAggregate._get()).buckets().array();
                    for (LongTermsBucket item : attrIdBuckets) {
                        EsProductRelatedInfo.ProductAttr attr = new EsProductRelatedInfo.ProductAttr();
                        attr.setAttrId(item.key());
                        List<String> attrValueList = new ArrayList<>();
                        List<StringTermsBucket> attrValues = ((StringTermsAggregate) item.aggregations().get("attrValues")._get()).buckets().array();
                        List<StringTermsBucket> attrNames = ((StringTermsAggregate) item.aggregations().get("attrNames")._get()).buckets().array();
                        for (StringTermsBucket attrValue : attrValues) {
                            attrValueList.add(attrValue.key().stringValue());
                        }
                        attr.setAttrValues(attrValueList);
                        if (!CollectionUtils.isEmpty(attrNames)) {
                            String attrName = attrNames.get(0).key().stringValue();
                            attr.setAttrName(attrName);
                        }
                        attrList.add(attr);
                    }
                }
            }
        }
        return productRelatedInfo;
    }
}
