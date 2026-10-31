/**
 * @author cjbi
 * 零代码应用管理模块。
 *
 * <p>应用与项目一一对齐：应用 ID 等于项目 ID，应用展示配置存储在项目 metadata 的 {@code app} 节点。</p>
 *
 * <p>按特性垂直分包：</p>
 * <ul>
 *   <li>{@code app} — 应用核心（App 实体、创建/更新/删除）</li>
 *   <li>{@code form} — 表单与字段定义</li>
 *   <li>{@code navigation} — 应用导航配置</li>
 *   <li>{@code record} — 记录读写、讨论与历史</li>
 *   <li>{@code workbench} — 工作台组件布局与数据</li>
 *   <li>{@code apppackage} — 应用包导入导出</li>
 *   <li>{@code template} — 内置应用模板目录</li>
 *   <li>{@code transfer} — 表单间数据传输</li>
 *   <li>{@code dataauthorization} — 表单级数据授权</li>
 *   <li>{@code approval} — 审批流</li>
 * </ul>
 *
 * <p>各特性包内的 DTO 位于对应 {@code *.dto} 子包。</p>
 */
package dev.flexmodel.apps;
