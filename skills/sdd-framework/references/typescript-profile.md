# TypeScript 语言配置

> SDD 框架 TypeScript 项目的标准配置和约束。

## 验证命令

### 编译检查

```bash
tsc --noEmit
```

零错误通过方可继续。

### Lint 检查

```bash
eslint .
biome check .
```

优先使用 biome（更快），备选 eslint。

### 测试

```bash
vitest run
jest
```

### 单文件测试

```bash
vitest run path/to/test.ts
```

### 构建

```bash
pnpm build
npm run build
```

---

## 禁止模式

以下模式在所有 TypeScript 代码中**绝对禁止**：

| 模式 | 原因 |
|------|------|
| `as any` | 绕过类型检查，引入运行时隐患 |
| `@ts-ignore` / `@ts-expect-error` | 掩盖类型错误，除非有明确的第三方库类型缺陷记录 |
| 空 catch 块 | `catch (e) { }` 吞掉错误不做任何处理 |
| 非空断言 `!` 无充分理由 | `value!` 应有明确的必要性说明 |
| `console.log` | 生产代码中禁止，替换为日志框架 |
| `eval()` / `Function()` | 安全风险，除非沙箱隔离场景 |
| `var` | 使用 `const` / `let` 替代 |
| 隐式 `any` 函数参数 | 必须显式标注类型 |
| 删除失败测试通过 CI | 禁止，应修复测试或标注 TODO |

---

## 测试策略

### 框架选择

- 单元测试：**Vitest**（推荐）或 Jest
- 组件测试：**Vue Test Utils**（Vue）或 **React Testing Library**（React）
- E2E：**Playwright**

### 目录结构

```
__tests__/                    # 集中式
src/utils/test.ts            # 或 co-located
src/components/__tests__/    # 组件测试
```

### TDD 循环

1. **Red** — 写一个失败的测试
2. **Green** — 写最少量代码让测试通过
3. **Refactor** — 重构代码，保持测试通过

---

## 项目规范

### tsconfig

必须启用 `strict` 模式：

```json
{
  "compilerOptions": {
    "strict": true,
    "noImplicitAny": true,
    "strictNullChecks": true
  }
}
```

### 模块系统

优先使用 **ESM**：

```json
{
  "compilerOptions": {
    "module": "ESNext",
    "moduleResolution": "bundler"
  }
}
```

### 路径别名

```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  }
}
```

### Barrel Exports

每个公开模块提供 `index.ts` 统一导出：

```typescript
// src/api/index.ts
export { fetchUser } from './fetchUser';
export { createUser } from './createUser';
```

---

## 依赖管理

### pnpm 优先

```bash
pnpm install
pnpm add <package>
```

### workspace monorepo

如有多包结构，使用 pnpm workspace：

```yaml
# pnpm-workspace.yaml
packages:
  - 'packages/*'
```

### package.json scripts

```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "test": "vitest run",
    "test:watch": "vitest",
    "lint": "eslint .",
    "typecheck": "tsc --noEmit"
  }
}
```

---

## 代码理解工具

### LSP 工具

| 操作 | 命令 |
|------|------|
| 跳转定义 | `lsp_goto_definition` |
| 查找引用 | `lsp_find_references` |
| 符号搜索 | `lsp_symbols` |
| 诊断检查 | `lsp_diagnostics` |
| 重命名 | `lsp_rename` |

### AST 模式匹配

使用 `ast_grep_search` 查找 TypeScript 代码模式：

```bash
# 查找所有 console.log 调用
ast_grep --pattern 'console.log($MSG)' --lang typescript

# 查找所有 useEffect（React）
ast_grep --pattern 'useEffect(() => { $$$ }, $DEP)' --lang typescript
```

### GitNexus 集成

GitNexus 原生支持 TypeScript，可进行：

- **影响分析** — `gitnexus_impact` 追踪符号变更范围
- **调用链追溯** — `gitnexus_query` 查找执行流
- **上下文查看** — `gitnexus_context` 获取符号完整视图
- **变更检测** — `gitnexus_detect_changes` 验证影响面

---

## 类型安全实践

### 避免类型断言

```typescript
// 禁止
const value = data as string;

// 推荐
const value: string = data; // 如果确定类型，声明参数类型
```

### 善用 utility types

```typescript
type User = {
  id: number;
  name: string;
  email: string;
};

type UserPreview = Pick<User, 'id' 'name'>;
type UserUpdate = Partial<User>;
```

### discriminated unions

```typescript
type Result =
  | { ok: true; value: User }
  | { ok: false; error: string };
```

---

## 性能注意

- 避免在渲染路径中使用 `JSON.parse(JSON.stringify())`
- 大数据量使用 `readonly` 避免不必要复制
- 善用 `satisfies` 验证类型而非标注
