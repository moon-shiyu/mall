# 订单列表查询增强 — 验证说明

## 一、修改文件清单

| 文件 | 修改内容 |
|------|---------|
| `mall-admin/.../dto/OmsOrderQueryParam.java` | 新增 `statusList`, `createTimeFrom`, `createTimeTo`, `receiverPhone`, `payType` |
| `mall-admin/.../resources/dao/OmsOrderDao.xml` | 新增动态 SQL 条件 + `ORDER BY create_time DESC` |
| `mall-admin/.../service/OmsOrderService.java` | 新增 `validateQueryParam()`, `exportList()` |
| `mall-admin/.../service/impl/OmsOrderServiceImpl.java` | 实现参数校验 + 导出逻辑 |
| `mall-admin/.../controller/OmsOrderController.java` | 新增 `GET /order/export` 端点 |

## 二、接口验证（curl）

假设服务运行在 `http://localhost:8080`，已获取 JWT token 存放于 `$TOKEN`。

### 1. 单条件 — 按订单编号（兼容旧逻辑）
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?orderSn=201809150101000001&pageSize=5&pageNum=1"
```
预期：返回该订单编号的精确匹配结果。

### 2. 单条件 — 按收货人手机号精确匹配
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?receiverPhone=13800138000&pageSize=5&pageNum=1"
```
预期：只返回 `receiver_phone = '13800138000'` 的订单。

### 3. 单条件 — 按支付方式
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?payType=1&pageSize=5&pageNum=1"
```
预期：只返回支付宝支付的订单。

### 4. 时间范围查询
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?createTimeFrom=2024-01-01+00:00:00&createTimeTo=2024-06-30+23:59:59&pageSize=10&pageNum=1"
```
预期：返回指定时间范围内的订单，按 `create_time DESC` 排序。

### 5. 多状态集合筛选
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?statusList=1&statusList=2&statusList=3&pageSize=10&pageNum=1"
```
预期：返回待发货 + 已发货 + 已完成的订单。

### 6. 组合条件
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?createTimeFrom=2024-01-01+00:00:00&createTimeTo=2024-12-31+23:59:59&statusList=1&statusList=2&receiverPhone=13800138000&sourceType=1&pageSize=10&pageNum=1"
```
预期：所有条件取交集。

### 7. 空集合 / null 安全
```bash
# statusList 不传 — 应忽略该条件
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?pageSize=5&pageNum=1"
```
预期：返回所有订单（仅 `delete_status=0`），按 `create_time DESC` 排序。

### 8. 分页验证
```bash
# 第1页
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?pageSize=3&pageNum=1"
# 第2页
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?pageSize=3&pageNum=2"
```
预期：两页数据无重复，总数一致。

### 9. 排序验证
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?pageSize=20&pageNum=1" | jq '.data.list[].createTime'
```
预期：时间严格递减（DESC）。

### 10. 非法参数 — 时间格式错误
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?createTimeFrom=2024-13-01&pageSize=5&pageNum=1"
```
预期：返回 `{"code": 500, "message": "起始时间格式错误，正确格式：yyyy-MM-dd HH:mm:ss"}`

### 11. 非法参数 — 起始时间晚于截止时间
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?createTimeFrom=2025-01-01+00:00:00&createTimeTo=2024-01-01+00:00:00&pageSize=5&pageNum=1"
```
预期：返回 `{"code": 500, "message": "起始时间不能晚于截止时间"}`

### 12. 非法参数 — 手机号格式错误
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?receiverPhone=123&pageSize=5&pageNum=1"
```
预期：返回 `{"code": 500, "message": "收货人手机号格式不正确"}`

### 13. 非法参数 — 状态值越界
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/list?statusList=99&pageSize=5&pageNum=1"
```
预期：返回 `{"code": 500, "message": "订单状态值不合法，允许范围：0-5"}`

### 14. 导出 — 与查询口径一致
```bash
# 导出与步骤4相同条件的数据
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/order/export?createTimeFrom=2024-01-01+00:00:00&createTimeTo=2024-06-30+23:59:59" \
  -o order_export.csv
```
预期：下载 CSV 文件，内容与 `/order/list` 同条件下查询结果一致（不分页，全量导出）。

## 三、SQL 验证（直连数据库）

```sql
-- 验证1：时间范围 + 多状态 + 排序
SELECT * FROM oms_order
WHERE delete_status = 0
  AND create_time >= '2024-01-01 00:00:00'
  AND create_time <= '2024-06-30 23:59:59'
  AND `status` IN (1, 2, 3)
ORDER BY create_time DESC
LIMIT 10;

-- 验证2：手机号精确匹配
SELECT * FROM oms_order
WHERE delete_status = 0
  AND receiver_phone = '13800138000'
ORDER BY create_time DESC;

-- 验证3：支付方式
SELECT * FROM oms_order
WHERE delete_status = 0
  AND pay_type = 1
ORDER BY create_time DESC
LIMIT 5;

-- 验证4：全量（无筛选条件），确认排序
SELECT id, order_sn, create_time, `status`, pay_type, source_type, receiver_phone
FROM oms_order
WHERE delete_status = 0
ORDER BY create_time DESC
LIMIT 20;

-- 验证5：分页无重叠 — 对比两页数据ID
SELECT id FROM oms_order WHERE delete_status = 0 ORDER BY create_time DESC LIMIT 3 OFFSET 0;
SELECT id FROM oms_order WHERE delete_status = 0 ORDER BY create_time DESC LIMIT 3 OFFSET 3;
-- 两组ID不应有交集
```

## 四、兼容性说明

| 旧参数 | 兼容性 |
|--------|--------|
| `orderSn` | 完全保留，精确匹配 |
| `receiverKeyword` | 完全保留，模糊匹配姓名/号码 |
| `status` | 完全保留，单值精确匹配 |
| `orderType` | 完全保留 |
| `sourceType` | 完全保留 |
| `createTime` | 完全保留，按日期前缀 LIKE 匹配 |

新增参数均为可选，不传时不影响旧逻辑。`statusList` 与 `status` 可同时传入（取交集），建议前端互斥使用。
