# Input policy tests

计划覆盖：

- pointer ID 注册与复用；
- pen/touch owner 不动态移交；
- single touch pending；
- two-finger transform；
- block-until-all-up；
- complete/abort 互斥幂等；
- cancel 后迟到 release 不提交；
- 生命周期重置；
- 同一拖动只生成一个历史事务。

测试应优先依赖平台无关的输入记录和状态机，不要求连接真机。
