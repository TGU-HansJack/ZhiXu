# 智续日历组件

仿滴答清单风格的日历选择器组件，支持农历、节气、节假日和调休显示。

## 📸 效果预览

日历组件特点：
- ✅ 固定6行网格布局（防止月份切换时跳动）
- ✅ 农历日期显示（1900-2100年）
- ✅ 24节气显示
- ✅ 法定节假日和农历节日标记
- ✅ 调休工作日角标（"休"/"班"）
- ✅ 圆形选中效果
- ✅ Material Design 3 风格

## 🚀 快速开始

### 1. 简单日期选择器

```kotlin
var showDialog by remember { mutableStateOf(false) }
var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

Button(onClick = { showDialog = true }) {
    Text("选择日期")
}

if (showDialog) {
    SimpleDatePickerDialog(
        initialDate = selectedDate ?: LocalDate.now(),
        onDateSelected = { date ->
            selectedDate = date
        },
        onDismiss = { showDialog = false }
    )
}
```

### 2. 完整版（带时间段快捷选择）

```kotlin
DatePickerDialog(
    initialDate = LocalDate.now(),
    showTimePeriodTab = true,  // 显示"时间段"标签页
    onDateSelected = { date ->
        // 处理选中的日期
    },
    onDismiss = { /* 关闭对话框 */ }
)
```

### 3. 嵌入式日历网格

```kotlin
var currentMonth by remember { mutableStateOf(YearMonth.now()) }
var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

CalendarGrid(
    currentMonth = currentMonth,
    selectedDate = selectedDate,
    onMonthChange = { currentMonth = it },
    onDateSelect = { selectedDate = it }
)
```

## 📦 组件结构

```
app.zhixu.ui.components.calendar/
├── LunarCalendar.kt          // 农历算法（查表法，1900-2100）
├── Holiday.kt                // 节假日数据和调休配置
├── CalendarDayCell.kt        // 日期单元格UI组件
├── CalendarGrid.kt           // 6x7网格 + 月份导航
├── DatePickerDialog.kt       // 日期选择器对话框
└── CalendarUsageExample.kt   // 使用示例和文档
```

## 🎨 设计实现

### 日期单元格（DayCell）

每个日期单元格包含：
- **主日期数字**：大号字体，选中时加粗
- **农历/节日/节气**：小号字体，优先级：节气 > 公历节日 > 农历节日 > 农历日期
- **调休角标**：圆形角标，绿色"休"或红色"班"

```kotlin
CalendarDayCell(
    day = DayModel(...),
    isSelected = false,
    onClick = { /* 点击事件 */ }
)
```

### 月份网格布局

- **固定6行**：始终显示42个单元格（6行×7列）
- **星期标题**：一、二、三、四、五、六、日
- **月份导航**：上月/下月/今天三个按钮

### 颜色系统

| 元素 | 颜色来源 |
|------|----------|
| 选中背景 | `MaterialTheme.colorScheme.primary` |
| 选中文字 | `MaterialTheme.colorScheme.onPrimary` |
| 今天背景 | `primaryContainer` 半透明 |
| 法定节假日 | `error` 红色 |
| 农历节日 | `tertiary` 绿色 |
| 节气 | `primary` 蓝色半透明 |
| 休息角标 | `#4CAF50` 绿色 |
| 工作角标 | `#F44336` 红色 |

## 🔧 自定义配置

### 添加节假日

编辑 [Holiday.kt](Holiday.kt) 的 `FIXED_HOLIDAYS`:

```kotlin
private val FIXED_HOLIDAYS = listOf(
    Holiday("公司周年庆", HolidayType.TRADITIONAL, MonthDay.of(6, 1)),
    // ...
)
```

### 配置调休安排

编辑 `getWorkDayStatus()` 方法：

```kotlin
fun getWorkDayStatus(date: LocalDate, year: Int): WorkDayStatus {
    if (year == 2026) {
        return when {
            // 元旦假期：1月1-3日休息
            date.monthValue == 1 && date.dayOfMonth in 1..3 -> WorkDayStatus.REST

            // 春节调休：1月24日上班
            date.monthValue == 1 && date.dayOfMonth == 24 -> WorkDayStatus.WORK

            // ...
        }
    }
    return WorkDayStatus.NONE
}
```

### 修改农历算法

农历数据存储在 `LunarCalendar.kt` 的 `LUNAR_INFO` 数组中：
- 支持范围：1900-2100年
- 算法：查表法（精度高，速度快）
- 数据格式：每个元素表示一年的农历信息（闰月、大小月）

## 💡 使用场景

1. **任务管理应用**：选择任务截止日期
2. **日程安排**：创建日程事件
3. **提醒应用**：设置提醒日期
4. **备忘录**：记录重要日期
5. **习惯打卡**：选择打卡日期

## 📐 技术细节

### 为什么固定6行？

防止月份切换时UI跳动：
- 某些月份需要5行（如2026年2月）
- 某些月份需要6行（如2026年1月）
- 固定6行可以保持布局稳定

### 农历算法原理

使用查表法：
1. 预存1900-2100年的农历年份信息
2. 每个年份用一个Long整数表示（位运算）
3. 从基准日期（1900年1月31日=农历正月初一）开始计算偏移

优点：
- ✅ 精度高（数据来自权威天文历法）
- ✅ 速度快（O(1)查表 + 简单计算）
- ✅ 无需网络请求

### 节气简化处理

当前实现使用固定日期范围估算节气（误差±2天）。

如需精确计算，可替换为天文算法：
```kotlin
// 使用太阳黄经计算节气（需要额外依赖）
fun getSolarTermExact(date: LocalDate): SolarTerm? {
    // 实现精确节气算法
}
```

## 🔍 测试方法

在任意 Screen 中添加测试代码：

```kotlin
@Composable
fun TestScreen() {
    var show by remember { mutableStateOf(false) }

    Button(onClick = { show = true }) {
        Text("打开日历")
    }

    if (show) {
        DatePickerDialog(
            onDateSelected = { println("选择了: $it") },
            onDismiss = { show = false }
        )
    }
}
```

或直接使用内置的测试界面：

```kotlin
CalendarUsageExampleScreen()
```

## 📝 API 文档

### DatePickerDialog

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `initialDate` | `LocalDate` | `LocalDate.now()` | 初始选中日期 |
| `showTimePeriodTab` | `Boolean` | `true` | 是否显示"时间段"标签页 |
| `onDateSelected` | `(LocalDate) -> Unit` | - | 日期选中回调 |
| `onDismiss` | `() -> Unit` | - | 对话框关闭回调 |

### CalendarGrid

| 参数 | 类型 | 说明 |
|------|------|------|
| `currentMonth` | `YearMonth` | 当前显示的月份 |
| `selectedDate` | `LocalDate?` | 选中的日期 |
| `onMonthChange` | `(YearMonth) -> Unit` | 月份切换回调 |
| `onDateSelect` | `(LocalDate) -> Unit` | 日期选择回调 |

### DayModel

```kotlin
data class DayModel(
    val date: LocalDate,           // 公历日期
    val lunarDate: LunarDate,      // 农历日期
    val solarTerm: SolarTerm?,     // 节气
    val holiday: Holiday?,         // 公历节日
    val lunarHoliday: String?,     // 农历节日
    val workDayStatus: WorkDayStatus, // 调休状态
    val isCurrentMonth: Boolean,   // 是否当前月
    val isToday: Boolean           // 是否今天
)
```

## 🎯 最佳实践

### ✅ 推荐做法

```kotlin
// 1. 使用 remember 保存状态
var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

// 2. 提供初始值
DatePickerDialog(
    initialDate = selectedDate ?: LocalDate.now(),
    // ...
)

// 3. 处理空值
onDateSelected = { date ->
    selectedDate = date
    // 执行后续操作
}
```

### ❌ 避免做法

```kotlin
// 不要在每次重组时都创建新对象
DatePickerDialog(
    initialDate = LocalDate.now(),  // ❌ 每次都是新值
    // ...
)

// 不要忘记处理 null
val date: LocalDate? = null
date.year  // ❌ 可能崩溃
```

## 📄 License

本组件遵循项目整体协议。

## 🤝 贡献

欢迎提交 Issue 和 PR！

需要改进的地方：
- [ ] 节气精确计算（使用天文算法）
- [ ] 支持自定义主题色
- [ ] 添加动画效果
- [ ] 支持日期范围选择
- [ ] 持久化调休配置（从服务器加载）

---

**创建时间**: 2026-01-04
**维护者**: ZhiXu Team
