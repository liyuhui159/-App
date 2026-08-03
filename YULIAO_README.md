# 语聊（Yuliao）

语聊是一个独立的原生 Android 聊天回复助手模块，位于 `yuliao/`，不会修改现有的“灵犀记账”应用逻辑。

## 已实现功能

- 粘贴剪贴板中的一句话或一段聊天内容
- 从微信、QQ、短信等应用通过“分享文字”打开语聊
- 选中文字后通过 Android 的 `PROCESS_TEXT` 菜单调用语聊
- 场景选择：日常、暧昧、朋友互怼、破冰、缓和气氛、职场
- 人设选择：幽默风趣、高情商温柔、轻松俏皮、克制礼貌、毒舌但不伤人、真诚直接
- 回复长度与生成数量设置
- 一次生成多条差异化回复
- 单条复制、系统分享、继续优化
- 可配置 API 地址、API Key 和模型名称
- 支持 OpenAI Responses API，并兼容常见 Chat Completions 接口

## 构建

```bash
./gradlew :yuliao:assembleDebug
```

APK 输出位置：

```text
yuliao/build/outputs/apk/debug/yuliao-debug.apk
```

也可以在 GitHub Actions 页面手动运行 `Build Yuliao APK`，完成后下载 `yuliao-debug-apk` 构建产物。

## 首次使用

1. 安装并打开“语聊”。
2. 点击右上角“API 设置”。
3. 填写 API 地址、API Key 和模型名称。
4. 默认 API 地址为 `https://api.openai.com/v1/responses`，默认模型为 `gpt-5-mini`。
5. 粘贴聊天内容，选择场景和人设，点击“生成多种回复”。

## API Key 安全说明

API Key 不会写入代码或提交到 GitHub，只保存在当前设备的 App 私有数据中。该方式适合个人测试。若未来公开发布或多人使用，应增加自有后端，由服务器保管 API Key，App 只调用你的业务接口。

## 后续建议

- 增加聊天上下文连续分析
- 增加收藏夹和历史记录
- 增加自定义人设库
- 增加悬浮窗快捷入口
- 增加截图识别聊天内容
- 增加账号体系和会员额度
