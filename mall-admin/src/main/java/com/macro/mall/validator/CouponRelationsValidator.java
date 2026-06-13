package com.macro.mall.validator;

import com.macro.mall.dto.SmsCouponParam;
import com.macro.mall.model.SmsCouponProductCategoryRelation;
import com.macro.mall.model.SmsCouponProductRelation;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 优惠券关联列表校验器
 * 根据 useType 校验关联商品/分类列表的完整性：
 * - useType=2 时必须有非空的商品关联列表，且 productId 不为 null、不重复
 * - useType=1 时必须有非空的分类关联列表，且 productCategoryId 不为 null、不重复
 * - useType=0 或 null 时放行
 * Created by macro on 2018/8/28.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Constraint(validatedBy = CouponRelationsValidator.CouponRelationsValidatorImpl.class)
public @interface ValidCouponRelations {

    String message() default "优惠券关联列表校验失败";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class CouponRelationsValidatorImpl implements ConstraintValidator<ValidCouponRelations, SmsCouponParam> {

        @Override
        public void initialize(ValidCouponRelations constraintAnnotation) {
            // no-op
        }

        @Override
        public boolean isValid(SmsCouponParam param, ConstraintValidatorContext ctx) {
            if (param == null || param.getUseType() == null) {
                return true;
            }

            // 禁止默认消息，使用自定义字段级消息
            ctx.disableDefaultConstraintViolation();
            boolean valid = true;

            Integer useType = param.getUseType();

            if (Integer.valueOf(2).equals(useType)) {
                valid = validateProductRelations(param.getProductRelationList(), ctx);
            } else if (Integer.valueOf(1).equals(useType)) {
                valid = validateCategoryRelations(param.getProductCategoryRelationList(), ctx);
            }
            // useType=0 或其他值：放行

            return valid;
        }

        /**
         * 校验商品关联列表
         */
        private boolean validateProductRelations(List<SmsCouponProductRelation> list,
                                                 ConstraintValidatorContext ctx) {
            if (list == null || list.isEmpty()) {
                ctx.buildConstraintViolationWithTemplate("使用类型为指定商品时，商品关联列表不能为空")
                        .addPropertyNode("productRelationList")
                        .addConstraintViolation();
                return false;
            }

            boolean valid = true;
            Set<Long> seenIds = new HashSet<>();

            for (int i = 0; i < list.size(); i++) {
                SmsCouponProductRelation relation = list.get(i);
                if (relation == null || relation.getProductId() == null) {
                    ctx.buildConstraintViolationWithTemplate("商品关联列表中第" + (i + 1) + "项的商品ID不能为空")
                            .addPropertyNode("productRelationList")
                            .addConstraintViolation();
                    valid = false;
                    continue;
                }
                if (!seenIds.add(relation.getProductId())) {
                    ctx.buildConstraintViolationWithTemplate("商品关联列表中存在重复的商品ID: " + relation.getProductId())
                            .addPropertyNode("productRelationList")
                            .addConstraintViolation();
                    valid = false;
                }
            }
            return valid;
        }

        /**
         * 校验分类关联列表
         */
        private boolean validateCategoryRelations(List<SmsCouponProductCategoryRelation> list,
                                                   ConstraintValidatorContext ctx) {
            if (list == null || list.isEmpty()) {
                ctx.buildConstraintViolationWithTemplate("使用类型为指定分类时，分类关联列表不能为空")
                        .addPropertyNode("productCategoryRelationList")
                        .addConstraintViolation();
                return false;
            }

            boolean valid = true;
            Set<Long> seenIds = new HashSet<>();

            for (int i = 0; i < list.size(); i++) {
                SmsCouponProductCategoryRelation relation = list.get(i);
                if (relation == null || relation.getProductCategoryId() == null) {
                    ctx.buildConstraintViolationWithTemplate("分类关联列表中第" + (i + 1) + "项的分类ID不能为空")
                            .addPropertyNode("productCategoryRelationList")
                            .addConstraintViolation();
                    valid = false;
                    continue;
                }
                if (!seenIds.add(relation.getProductCategoryId())) {
                    ctx.buildConstraintViolationWithTemplate("分类关联列表中存在重复的分类ID: " + relation.getProductCategoryId())
                            .addPropertyNode("productCategoryRelationList")
                            .addConstraintViolation();
                    valid = false;
                }
            }
            return valid;
        }
    }
}
