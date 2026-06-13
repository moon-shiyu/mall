package com.macro.mall;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.dao.OmsOrderDao;
import com.macro.mall.dto.OmsOrderQueryParam;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.service.OmsOrderService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单查询增强功能测试
 * 覆盖：单条件、多条件、空集合、时间范围、分页排序、参数校验
 */
@SpringBootTest
public class OmsOrderDaoTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(OmsOrderDaoTests.class);

    @Autowired
    private OmsOrderDao orderDao;

    @Autowired
    private OmsOrderService orderService;

    // ===== 单条件查询 =====

    @Test
    public void testQueryByOrderSn() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setOrderSn("201809150101000001");
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("按订单编号查询: {} 条", list.size());
        for (OmsOrder o : list) {
            assertEquals("201809150101000001", o.getOrderSn());
        }
    }

    @Test
    public void testQueryBySingleStatus() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setStatus(0);
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("按单个状态(待付款)查询: {} 条", list.size());
        for (OmsOrder o : list) {
            assertEquals(0, (int) o.getStatus());
        }
    }

    @Test
    public void testQueryByReceiverPhone() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setReceiverPhone("13800138000");
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("按收货人手机号查询: {} 条", list.size());
        for (OmsOrder o : list) {
            assertEquals("13800138000", o.getReceiverPhone());
        }
    }

    @Test
    public void testQueryByPayType() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setPayType(1);
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("按支付方式(支付宝)查询: {} 条", list.size());
        for (OmsOrder o : list) {
            assertEquals(1, (int) o.getPayType());
        }
    }

    @Test
    public void testQueryBySourceType() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setSourceType(1);
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("按订单来源(app)查询: {} 条", list.size());
        for (OmsOrder o : list) {
            assertEquals(1, (int) o.getSourceType());
        }
    }

    // ===== 多条件组合查询 =====

    @Test
    public void testQueryMultiCondition() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setStatus(1);
        param.setSourceType(0);
        param.setCreateTimeFrom("2018-01-01 00:00:00");
        param.setCreateTimeTo("2019-12-31 23:59:59");
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("多条件组合查询: {} 条", list.size());
        for (OmsOrder o : list) {
            assertEquals(1, (int) o.getStatus());
            assertEquals(0, (int) o.getSourceType());
        }
    }

    // ===== statusList 多状态查询 =====

    @Test
    public void testQueryByStatusList() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setStatusList(Arrays.asList(0, 1, 2));
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("按状态集合[0,1,2]查询: {} 条", list.size());
        for (OmsOrder o : list) {
            assertTrue(Arrays.asList(0, 1, 2).contains(o.getStatus()),
                    "订单状态应在 [0,1,2] 范围内，实际: " + o.getStatus());
        }
    }

    // ===== 空集合不影响查询 =====

    @Test
    public void testQueryWithEmptyStatusList() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setStatusList(Collections.emptyList());
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("空状态集合查询(应忽略statusList): {} 条", list.size());
        // 空集合应被忽略，返回全部订单（delete_status=0）
        assertFalse(list.isEmpty(), "空状态集合不应导致空结果");
    }

    // ===== status 与 statusList 互斥：statusList 优先 =====

    @Test
    public void testStatusListTakesPriorityOverStatus() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setStatus(3);  // 已完成
        param.setStatusList(Arrays.asList(0, 1));  // 待付款、待发货
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("status=3 且 statusList=[0,1]（statusList应优先）: {} 条", list.size());
        for (OmsOrder o : list) {
            assertTrue(Arrays.asList(0, 1).contains(o.getStatus()),
                    "statusList应优先于status，实际状态: " + o.getStatus());
        }
    }

    // ===== 时间范围查询 =====

    @Test
    public void testQueryByTimeRange() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setCreateTimeFrom("2018-09-01 00:00:00");
        param.setCreateTimeTo("2018-09-30 23:59:59");
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("按时间范围(2018年9月)查询: {} 条", list.size());
        // 验证排序：create_time DESC
        for (int i = 1; i < list.size(); i++) {
            assertTrue(!list.get(i - 1).getCreateTime().before(list.get(i).getCreateTime()),
                    "结果应按 create_time DESC 排序");
        }
    }

    @Test
    public void testQueryByCreateTimePrefix() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setCreateTime("2018-09");
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("按日期前缀(2018-09)查询: {} 条", list.size());
    }

    // ===== 分页 + 排序 =====

    @Test
    public void testPaginationAndSorting() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        PageHelper.startPage(1, 3);
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("分页查询(第1页,每页3条): {} 条", list.size());
        assertEquals(3, list.size(), "每页3条应返回3条（假设总数>=3）");
        // 验证排序
        for (int i = 1; i < list.size(); i++) {
            assertTrue(!list.get(i - 1).getCreateTime().before(list.get(i).getCreateTime()),
                    "结果应按 create_time DESC 排序");
        }
    }

    @Test
    public void testPaginationPage2() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        PageHelper.startPage(2, 3);
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("分页查询(第2页,每页3条): {} 条", list.size());
    }

    // ===== 参数校验 =====

    @Test
    public void testValidateInvalidTimeFormat() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setCreateTimeFrom("2018/09/01");
        assertThrows(ApiException.class, () -> orderService.validateQueryParam(param),
                "非法时间格式应抛出 ApiException");
    }

    @Test
    public void testValidateTimeRangeReversed() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setCreateTimeFrom("2020-01-01 00:00:00");
        param.setCreateTimeTo("2019-01-01 00:00:00");
        assertThrows(ApiException.class, () -> orderService.validateQueryParam(param),
                "起始时间晚于截止时间应抛出 ApiException");
    }

    @Test
    public void testValidateInvalidPhone() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setReceiverPhone("12345");
        assertThrows(ApiException.class, () -> orderService.validateQueryParam(param),
                "非法手机号应抛出 ApiException");
    }

    @Test
    public void testValidateInvalidStatusInList() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setStatusList(Arrays.asList(0, 1, 99));
        assertThrows(ApiException.class, () -> orderService.validateQueryParam(param),
                "statusList 中含非法值 99 应抛出 ApiException");
    }

    @Test
    public void testValidateNullParam() {
        // null 参数不应抛异常
        assertDoesNotThrow(() -> orderService.validateQueryParam(null));
    }

    @Test
    public void testValidateValidParam() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setCreateTimeFrom("2024-01-01 00:00:00");
        param.setCreateTimeTo("2024-12-31 23:59:59");
        param.setReceiverPhone("13912345678");
        param.setStatusList(Arrays.asList(0, 1, 2, 3));
        param.setPayType(1);
        param.setSourceType(0);
        assertDoesNotThrow(() -> orderService.validateQueryParam(param),
                "合法参数不应抛出异常");
    }

    // ===== 无筛选条件（全量查询） =====

    @Test
    public void testQueryAllNoFilter() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("无筛选条件查询: {} 条", list.size());
        assertFalse(list.isEmpty(), "无筛选条件应返回所有未删除订单");
        // 验证排序
        for (int i = 1; i < list.size(); i++) {
            assertTrue(!list.get(i - 1).getCreateTime().before(list.get(i).getCreateTime()),
                    "结果应按 create_time DESC 排序");
        }
    }

    // ===== receiverKeyword 兼容 =====

    @Test
    public void testQueryByReceiverKeyword() {
        OmsOrderQueryParam param = new OmsOrderQueryParam();
        param.setReceiverKeyword("张");
        List<OmsOrder> list = orderDao.getList(param);
        LOGGER.info("按收货人关键字查询: {} 条", list.size());
        for (OmsOrder o : list) {
            boolean matchName = o.getReceiverName() != null && o.getReceiverName().contains("张");
            boolean matchPhone = o.getReceiverPhone() != null && o.getReceiverPhone().contains("张");
            assertTrue(matchName || matchPhone, "收货人姓名或手机号应包含'张'");
        }
    }
}
