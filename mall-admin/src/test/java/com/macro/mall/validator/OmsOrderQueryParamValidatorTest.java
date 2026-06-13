package com.macro.mall.validator;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.dto.OmsOrderQueryParam;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OmsOrderQueryParamValidator} 的聚焦单元测试，无需 Spring 容器与数据库。
 * 覆盖：空参数、单条件、多条件、空集合、时间范围归一化、非法时间范围、非法枚举等场景。
 */
public class OmsOrderQueryParamValidatorTest {

    private OmsOrderQueryParam newParam() {
        return new OmsOrderQueryParam();
    }

    @Test
    public void nullParam_doesNotThrow() {
        assertDoesNotThrow(() -> OmsOrderQueryParamValidator.validateAndNormalize(null));
    }

    @Test
    public void emptyParam_doesNotThrow() {
        assertDoesNotThrow(() -> OmsOrderQueryParamValidator.validateAndNormalize(newParam()));
    }

    @Test
    public void fullDateTimeRange_passesAndKeepsValues() {
        OmsOrderQueryParam param = newParam();
        param.setCreateTimeStart("2024-01-01 08:00:00");
        param.setCreateTimeEnd("2024-01-31 18:00:00");
        OmsOrderQueryParamValidator.validateAndNormalize(param);
        assertEquals("2024-01-01 08:00:00", param.getCreateTimeStart());
        assertEquals("2024-01-31 18:00:00", param.getCreateTimeEnd());
    }

    @Test
    public void dateOnlyRange_normalizedToDayBounds() {
        OmsOrderQueryParam param = newParam();
        param.setCreateTimeStart("2024-01-01");
        param.setCreateTimeEnd("2024-01-31");
        OmsOrderQueryParamValidator.validateAndNormalize(param);
        assertEquals("2024-01-01 00:00:00", param.getCreateTimeStart());
        assertEquals("2024-01-31 23:59:59", param.getCreateTimeEnd());
    }

    @Test
    public void onlyStartProvided_normalizedAndNoInversionCheck() {
        OmsOrderQueryParam param = newParam();
        param.setCreateTimeStart("2024-01-01");
        OmsOrderQueryParamValidator.validateAndNormalize(param);
        assertEquals("2024-01-01 00:00:00", param.getCreateTimeStart());
        assertNull(param.getCreateTimeEnd());
    }

    @Test
    public void surroundingWhitespace_isTrimmed() {
        OmsOrderQueryParam param = newParam();
        param.setCreateTimeStart("  2024-01-01  ");
        OmsOrderQueryParamValidator.validateAndNormalize(param);
        assertEquals("2024-01-01 00:00:00", param.getCreateTimeStart());
    }

    @Test
    public void equalBounds_doesNotThrow() {
        OmsOrderQueryParam param = newParam();
        param.setCreateTimeStart("2024-01-01 00:00:00");
        param.setCreateTimeEnd("2024-01-01 00:00:00");
        assertDoesNotThrow(() -> OmsOrderQueryParamValidator.validateAndNormalize(param));
    }

    @Test
    public void invertedRange_throwsWithClearMessage() {
        OmsOrderQueryParam param = newParam();
        param.setCreateTimeStart("2024-02-01");
        param.setCreateTimeEnd("2024-01-01");
        ApiException ex = assertThrows(ApiException.class,
                () -> OmsOrderQueryParamValidator.validateAndNormalize(param));
        assertTrue(ex.getMessage().contains("下单开始时间不能晚于结束时间"));
    }

    @Test
    public void invertedRange_sameDayTimeOrder_throws() {
        OmsOrderQueryParam param = newParam();
        param.setCreateTimeStart("2024-01-01 18:00:00");
        param.setCreateTimeEnd("2024-01-01 08:00:00");
        assertThrows(ApiException.class,
                () -> OmsOrderQueryParamValidator.validateAndNormalize(param));
    }

    @Test
    public void badStartFormat_throwsWithFieldHint() {
        OmsOrderQueryParam param = newParam();
        param.setCreateTimeStart("2024/01/01");
        ApiException ex = assertThrows(ApiException.class,
                () -> OmsOrderQueryParamValidator.validateAndNormalize(param));
        assertTrue(ex.getMessage().contains("下单开始时间格式不正确"));
    }

    @Test
    public void badEndFormat_throwsWithFieldHint() {
        OmsOrderQueryParam param = newParam();
        param.setCreateTimeEnd("not-a-date");
        ApiException ex = assertThrows(ApiException.class,
                () -> OmsOrderQueryParamValidator.validateAndNormalize(param));
        assertTrue(ex.getMessage().contains("下单结束时间格式不正确"));
    }

    @Test
    public void nullStatusList_doesNotThrow() {
        OmsOrderQueryParam param = newParam();
        param.setStatusList(null);
        assertDoesNotThrow(() -> OmsOrderQueryParamValidator.validateAndNormalize(param));
    }

    @Test
    public void emptyStatusList_doesNotThrow() {
        OmsOrderQueryParam param = newParam();
        param.setStatusList(new ArrayList<>());
        assertDoesNotThrow(() -> OmsOrderQueryParamValidator.validateAndNormalize(param));
    }

    @Test
    public void statusListWithinRange_doesNotThrow() {
        OmsOrderQueryParam param = newParam();
        param.setStatusList(Arrays.asList(0, 3, 5));
        assertDoesNotThrow(() -> OmsOrderQueryParamValidator.validateAndNormalize(param));
    }

    @Test
    public void statusListOutOfRange_throwsWithRangeMessage() {
        OmsOrderQueryParam param = newParam();
        param.setStatusList(Arrays.asList(0, 6));
        ApiException ex = assertThrows(ApiException.class,
                () -> OmsOrderQueryParamValidator.validateAndNormalize(param));
        assertTrue(ex.getMessage().contains("订单状态不合法"));
    }

    @Test
    public void statusListWithNullElement_throws() {
        OmsOrderQueryParam param = newParam();
        param.setStatusList(Collections.singletonList(null));
        assertThrows(ApiException.class,
                () -> OmsOrderQueryParamValidator.validateAndNormalize(param));
    }

    @Test
    public void payTypeWithinRange_doesNotThrow() {
        OmsOrderQueryParam param = newParam();
        param.setPayType(2);
        assertDoesNotThrow(() -> OmsOrderQueryParamValidator.validateAndNormalize(param));
    }

    @Test
    public void payTypeOutOfRange_throwsWithRangeMessage() {
        OmsOrderQueryParam param = newParam();
        param.setPayType(3);
        ApiException ex = assertThrows(ApiException.class,
                () -> OmsOrderQueryParamValidator.validateAndNormalize(param));
        assertTrue(ex.getMessage().contains("支付方式不合法"));
    }

    @Test
    public void multipleConditionsTogether_passAndNormalize() {
        OmsOrderQueryParam param = newParam();
        param.setOrderSn("202401010001");
        param.setStatusList(Arrays.asList(1, 2));
        param.setPayType(1);
        param.setReceiverPhone("138");
        param.setCreateTimeStart("2024-01-01");
        param.setCreateTimeEnd("2024-01-31");
        OmsOrderQueryParamValidator.validateAndNormalize(param);
        assertEquals("2024-01-01 00:00:00", param.getCreateTimeStart());
        assertEquals("2024-01-31 23:59:59", param.getCreateTimeEnd());
    }
}
