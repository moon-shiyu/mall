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
import org.springframework.transaction.annotation.Transactional;
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
    private PortalProductDao productDao;
    @Autowired
    private OmsPromotionService promotionService;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;

    @Override
    @Transactional
    public int add(OmsCartItem cartItem) {
        int count;
        UmsMember currentMember =memberService.getCurrentMember();
        cartItem.setMemberId(currentMember.getId());
        cartItem.setMemberNickname(currentMember.getNickname());
        cartItem.setDeleteStatus(0);
        OmsCartItem existCartItem = getCartItem(cartItem);
        if (existCartItem == null) {
            // 新增：existingQuantity = 0
            validateCartItem(cartItem, 0);
            cartItem.setCreateDate(new Date());
            count = cartItemMapper.insert(cartItem);
        } else {
            // 合并：existingQuantity = 已有数量
            validateCartItem(cartItem, existCartItem.getQuantity());
            cartItem.setModifyDate(new Date());
            existCartItem.setQuantity(existCartItem.getQuantity() + cartItem.getQuantity());
            count = cartItemMapper.updateByPrimaryKey(existCartItem);
        }
        return count;
    }

    /**
     * 校验商品可售状态、SKU有效性、数量合法性、库存充足性
     * @param cartItem 待校验的购物车项
     * @param existingQuantity 已有购物车中的数量（新增时为0，合并时为已有数量）
     */
    private void validateCartItem(OmsCartItem cartItem, int existingQuantity) {
        // 1. 数量校验
        if (cartItem.getQuantity() == null || cartItem.getQuantity() <= 0) {
            Asserts.fail("商品数量不合法");
        }

        // 2. 商品上架状态校验
        PmsProduct product = productMapper.selectByPrimaryKey(cartItem.getProductId());
        if (product == null) {
            Asserts.fail("商品不存在");
        }
        if (product.getDeleteStatus() != null && product.getDeleteStatus() == 1) {
            Asserts.fail("商品已删除");
        }
        if (product.getPublishStatus() == null || product.getPublishStatus() != 1) {
            Asserts.fail("商品已下架");
        }

        // 3. SKU 有效性校验
        if (cartItem.getProductSkuId() == null) {
            Asserts.fail("请选择商品规格");
        }
        PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(cartItem.getProductSkuId());
        if (skuStock == null) {
            Asserts.fail("商品规格不存在");
        }
        if (!skuStock.getProductId().equals(cartItem.getProductId())) {
            Asserts.fail("商品规格与商品不匹配");
        }

        // 4. 库存校验（可用库存 = stock - lockStock）
        int realStock = (skuStock.getStock() == null ? 0 : skuStock.getStock())
                      - (skuStock.getLockStock() == null ? 0 : skuStock.getLockStock());
        int totalQuantity = existingQuantity + cartItem.getQuantity();
        if (totalQuantity > realStock) {
            Asserts.fail("库存不足，当前可用库存：" + realStock);
        }
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
    @Transactional
    public int updateQuantity(Long id, Long memberId, Integer quantity) {
        // 查出购物车项以获取商品和SKU信息
        OmsCartItem existCartItem = cartItemMapper.selectByPrimaryKey(id);
        if (existCartItem == null || existCartItem.getDeleteStatus() != 0
                || !existCartItem.getMemberId().equals(memberId)) {
            Asserts.fail("购物车商品不存在");
        }
        // 构造校验用对象
        OmsCartItem validateItem = new OmsCartItem();
        validateItem.setProductId(existCartItem.getProductId());
        validateItem.setProductSkuId(existCartItem.getProductSkuId());
        validateItem.setQuantity(quantity);
        validateCartItem(validateItem, 0);  // quantity 是绝对值，existingQuantity=0

        OmsCartItem cartItem = new OmsCartItem();
        cartItem.setQuantity(quantity);
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
    @Transactional
    public int updateAttr(OmsCartItem cartItem) {
        // 1. 查出旧记录并验证归属
        OmsCartItem oldItem = cartItemMapper.selectByPrimaryKey(cartItem.getId());
        if (oldItem == null || oldItem.getDeleteStatus() != 0) {
            Asserts.fail("购物车商品不存在");
        }
        UmsMember currentMember = memberService.getCurrentMember();
        if (!oldItem.getMemberId().equals(currentMember.getId())) {
            Asserts.fail("不能修改他人购物车");
        }

        // 2. 设置会员信息用于校验
        cartItem.setMemberId(currentMember.getId());
        cartItem.setMemberNickname(currentMember.getNickname());
        cartItem.setDeleteStatus(0);

        // 3. 检查新SKU是否已存在于其他购物车项（避免合并冲突）
        OmsCartItem duplicateItem = getCartItem(cartItem);
        int existingQuantity = 0;
        if (duplicateItem != null && !duplicateItem.getId().equals(oldItem.getId())) {
            existingQuantity = duplicateItem.getQuantity();
        }

        // 4. 先校验（在任何写入之前）
        validateCartItem(cartItem, existingQuantity);

        // 5. 校验通过后再执行写入
        OmsCartItem updateCart = new OmsCartItem();
        updateCart.setId(cartItem.getId());
        updateCart.setModifyDate(new Date());
        updateCart.setDeleteStatus(1);
        cartItemMapper.updateByPrimaryKeySelective(updateCart);

        // 6. 如果新SKU已有购物车项，合并数量；否则新增
        if (duplicateItem != null && !duplicateItem.getId().equals(oldItem.getId())) {
            duplicateItem.setQuantity(duplicateItem.getQuantity() + cartItem.getQuantity());
            duplicateItem.setModifyDate(new Date());
            cartItemMapper.updateByPrimaryKey(duplicateItem);
        } else {
            cartItem.setId(null);
            cartItem.setCreateDate(new Date());
            cartItemMapper.insert(cartItem);
        }
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
