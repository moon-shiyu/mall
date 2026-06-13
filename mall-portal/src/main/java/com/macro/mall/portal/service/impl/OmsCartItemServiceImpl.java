package com.macro.mall.portal.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.OmsCartItemExample;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.service.OmsCartItemService;
import com.macro.mall.portal.service.OmsPromotionService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 购物车管理Service实现类
 * Created by macro on 2018/8/2.
 */
@Service
public class OmsCartItemServiceImpl implements OmsCartItemService {
    @Autowired
    private OmsCartItemMapper cartItemMapper;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private PortalProductDao productDao;
    @Autowired
    private OmsPromotionService promotionService;
    @Autowired
    private UmsMemberService memberService;

    @Override
    public int add(OmsCartItem cartItem) {
        int count;
        UmsMember currentMember = memberService.getCurrentMember();
        cartItem.setMemberId(currentMember.getId());
        cartItem.setMemberNickname(currentMember.getNickname());
        cartItem.setDeleteStatus(0);
        //校验数量、商品上架状态与SKU归属
        validateQuantity(cartItem.getQuantity());
        PmsSkuStock skuStock = validateProductAndSku(cartItem.getProductId(), cartItem.getProductSkuId());
        OmsCartItem existCartItem = getCartItem(cartItem);
        if (existCartItem == null) {
            validateStock(skuStock, cartItem.getQuantity());
            cartItem.setCreateDate(new Date());
            count = cartItemMapper.insert(cartItem);
        } else {
            //同一会员同一商品同一SKU合并时，合并后的数量不能超过库存
            int mergedQuantity = existCartItem.getQuantity() + cartItem.getQuantity();
            validateStock(skuStock, mergedQuantity);
            existCartItem.setModifyDate(new Date());
            existCartItem.setQuantity(mergedQuantity);
            count = cartItemMapper.updateByPrimaryKey(existCartItem);
        }
        return count;
    }

    /**
     * 根据会员id,商品id和规格获取购物车中商品
     */
    private OmsCartItem getCartItem(OmsCartItem cartItem) {
        OmsCartItemExample example = new OmsCartItemExample();
        OmsCartItemExample.Criteria criteria = example.createCriteria().andMemberIdEqualTo(cartItem.getMemberId())
                .andProductIdEqualTo(cartItem.getProductId()).andDeleteStatusEqualTo(0);
        if (cartItem.getProductSkuId()!=null) {
            criteria.andProductSkuIdEqualTo(cartItem.getProductSkuId());
        }
        List<OmsCartItem> cartItemList = cartItemMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(cartItemList)) {
            return cartItemList.get(0);
        }
        return null;
    }

    /**
     * 根据购物车项id和会员id获取未删除的购物车项，用于会员隔离校验
     */
    private OmsCartItem getCartItem(Long id, Long memberId) {
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andIdEqualTo(id).andMemberIdEqualTo(memberId).andDeleteStatusEqualTo(0);
        List<OmsCartItem> cartItemList = cartItemMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(cartItemList)) {
            return cartItemList.get(0);
        }
        return null;
    }

    /**
     * 校验购买数量，最小购买数量为1
     */
    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            Asserts.fail("购买数量必须大于0");
        }
    }

    /**
     * 校验商品是否存在且已上架、SKU是否存在且归属该商品，返回对应SKU库存信息
     */
    private PmsSkuStock validateProductAndSku(Long productId, Long productSkuId) {
        PmsProduct product = productMapper.selectByPrimaryKey(productId);
        if (product == null || (product.getDeleteStatus() != null && product.getDeleteStatus() == 1)) {
            Asserts.fail("商品不存在");
        }
        if (product.getPublishStatus() == null || product.getPublishStatus() != 1) {
            Asserts.fail("商品已下架，无法操作");
        }
        if (productSkuId == null) {
            Asserts.fail("请选择商品规格");
        }
        PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(productSkuId);
        if (skuStock == null || !productId.equals(skuStock.getProductId())) {
            Asserts.fail("商品规格不存在");
        }
        return skuStock;
    }

    /**
     * 校验库存是否充足；同一SKU合并加购时，requiredQuantity需为合并后的总数量
     */
    private void validateStock(PmsSkuStock skuStock, int requiredQuantity) {
        Integer stock = skuStock.getStock();
        if (stock == null || stock < requiredQuantity) {
            Asserts.fail("商品库存不足，当前库存：" + (stock == null ? 0 : stock) + "，需要数量：" + requiredQuantity);
        }
    }

    @Override
    public List<OmsCartItem> list(Long memberId) {
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0).andMemberIdEqualTo(memberId);
        return cartItemMapper.selectByExample(example);
    }

    @Override
    public List<CartPromotionItem> listPromotion(Long memberId, List<Long> cartIds) {
        List<OmsCartItem> cartItemList = list(memberId);
        if(CollUtil.isNotEmpty(cartIds)){
            cartItemList = cartItemList.stream().filter(item->cartIds.contains(item.getId())).collect(Collectors.toList());
        }
        List<CartPromotionItem> cartPromotionItemList = new ArrayList<>();
        if(!CollectionUtils.isEmpty(cartItemList)){
            cartPromotionItemList = promotionService.calcCartPromotion(cartItemList);
        }
        return cartPromotionItemList;
    }

    @Override
    public int updateQuantity(Long id, Long memberId, Integer quantity) {
        validateQuantity(quantity);
        //仅能修改当前会员自己的购物车项，实现会员隔离
        OmsCartItem existCartItem = getCartItem(id, memberId);
        if (existCartItem == null) {
            Asserts.fail("购物车中商品不存在");
        }
        PmsSkuStock skuStock = validateProductAndSku(existCartItem.getProductId(), existCartItem.getProductSkuId());
        validateStock(skuStock, quantity);
        OmsCartItem cartItem = new OmsCartItem();
        cartItem.setQuantity(quantity);
        cartItem.setModifyDate(new Date());
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0)
                .andIdEqualTo(id).andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(cartItem, example);
    }

    @Override
    public int delete(Long memberId, List<Long> ids) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andIdIn(ids).andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(record, example);
    }

    @Override
    public CartProduct getCartProduct(Long productId) {
        return productDao.getCartProduct(productId);
    }

    @Override
    public int updateAttr(OmsCartItem cartItem) {
        UmsMember currentMember = memberService.getCurrentMember();
        Long originalId = cartItem.getId();
        //先校验新规格(数量、上架状态、SKU库存)，校验失败直接抛出异常，此时尚未删除原购物车项
        cartItem.setMemberId(currentMember.getId());
        cartItem.setMemberNickname(currentMember.getNickname());
        cartItem.setDeleteStatus(0);
        validateQuantity(cartItem.getQuantity());
        validateProductAndSku(cartItem.getProductId(), cartItem.getProductSkuId());
        //删除原购物车项(限定当前会员，避免越权)，与下方新增处于同一事务，任一步骤失败则整体回滚，购物车数据不会丢失
        OmsCartItem updateCart = new OmsCartItem();
        updateCart.setModifyDate(new Date());
        updateCart.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andIdEqualTo(originalId).andMemberIdEqualTo(currentMember.getId());
        cartItemMapper.updateByExampleSelective(updateCart, example);
        //新增新规格购物车项，复用add的库存与合并校验
        cartItem.setId(null);
        add(cartItem);
        return 1;
    }

    @Override
    public int clear(Long memberId) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(record,example);
    }
}
