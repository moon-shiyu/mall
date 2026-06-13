package com.macro.mall.validator;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.dto.OmsOrderQueryParam;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 订单查询参数校验与归一化工具。
 * <p>
 * 校验失败时统一通过 {@link Asserts#fail(String)} 抛出 {@code ApiException}，
 * 由 {@code GlobalExceptionHandler} 转换为 {@code CommonResult} 风格的错误响应。
 * <p>
 * 仅对新增筛选条件（订单状态集合、支付方式、下单时间范围）做严格校验，
 * 不改变 orderSn、receiverKeyword、status、sourceType 等既有字段的宽松行为。
 */
public class OmsOrderQueryParamValidator {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int STATUS_MIN = 0;
    private static final int STATUS_MAX = 5;
    private static final int PAY_TYPE_MIN = 0;
    private static final int PAY_TYPE_MAX = 2;

    private OmsOrderQueryParamValidator() {
    }

    /**
     * 校验并归一化查询参数。日期范围会被归一化为 {@code yyyy-MM-dd HH:mm:ss}：
     * 仅传日期时，开始时间补全为当天 00:00:00、结束时间补全为当天 23:59:59，保证范围闭区间且不漏数据。
     *
     * @param queryParam 订单查询参数，可为 {@code null}
     */
    public static void validateAndNormalize(OmsOrderQueryParam queryParam) {
        if (queryParam == null) {
            return;
        }
        validateStatusList(queryParam.getStatusList());
        validatePayType(queryParam.getPayType());
        normalizeAndValidateTimeRange(queryParam);
    }

    private static void validateStatusList(List<Integer> statusList) {
        if (CollectionUtils.isEmpty(statusList)) {
            return;
        }
        for (Integer status : statusList) {
            if (status == null || status < STATUS_MIN || status > STATUS_MAX) {
                Asserts.fail("订单状态不合法，取值范围为" + STATUS_MIN + "-" + STATUS_MAX);
            }
        }
    }

    private static void validatePayType(Integer payType) {
        if (payType == null) {
            return;
        }
        if (payType < PAY_TYPE_MIN || payType > PAY_TYPE_MAX) {
            Asserts.fail("支付方式不合法，取值范围为" + PAY_TYPE_MIN + "-" + PAY_TYPE_MAX);
        }
    }

    private static void normalizeAndValidateTimeRange(OmsOrderQueryParam queryParam) {
        LocalDateTime start = null;
        LocalDateTime end = null;
        if (StringUtils.hasText(queryParam.getCreateTimeStart())) {
            start = parse(queryParam.getCreateTimeStart().trim(), false, "下单开始时间");
            queryParam.setCreateTimeStart(start.format(DATE_TIME_FORMATTER));
        }
        if (StringUtils.hasText(queryParam.getCreateTimeEnd())) {
            end = parse(queryParam.getCreateTimeEnd().trim(), true, "下单结束时间");
            queryParam.setCreateTimeEnd(end.format(DATE_TIME_FORMATTER));
        }
        if (start != null && end != null && start.isAfter(end)) {
            Asserts.fail("下单开始时间不能晚于结束时间");
        }
    }

    /**
     * 解析时间字符串，优先按 {@code yyyy-MM-dd HH:mm:ss} 解析，失败再按 {@code yyyy-MM-dd} 解析。
     *
     * @param value      已去除首尾空白的时间字符串
     * @param endOfDay   仅传日期时，true 补全为 23:59:59，false 补全为 00:00:00
     * @param fieldLabel 错误提示中使用的字段名
     */
    private static LocalDateTime parse(String value, boolean endOfDay, String fieldLabel) {
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // 继续尝试按纯日期解析
        }
        try {
            LocalDate date = LocalDate.parse(value, DATE_FORMATTER);
            return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
        } catch (DateTimeParseException ignored) {
            Asserts.fail(fieldLabel + "格式不正确，正确格式为yyyy-MM-dd或yyyy-MM-dd HH:mm:ss");
        }
        // Asserts.fail 必抛异常，此处不可达
        return null;
    }
}
