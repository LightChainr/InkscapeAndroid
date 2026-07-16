# SVG regression corpus

本目录后续存放最小、可公开的 SVG 回归文件，包括：

```text
rect
ellipse/circle
path
group
text
embedded/linked image
clone/use
nested transform
viewBox
defs/gradient/style/filter
unknown extension elements
non-ASCII filename
large coordinate values
```

不要提交用户真实文档。每个样本应只验证一个结构或回归点，并提供结构化预期，而不是依赖字符串级 XML diff。
