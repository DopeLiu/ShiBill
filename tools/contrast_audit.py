"""对比度验收脚本（对应《拾账ShiBill-UI改造方案》§9.2）。

用法：
    python tools/contrast_audit.py

判定标准：WCAG 2.1 对比度，正文 >= 4.5:1。
本方案全部配色对已通过（最低 4.90:1）。改造完成后重新运行，确认无回归。
"""


def lum(hex_color: str) -> float:
    h = hex_color.lstrip('#')
    channels = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    linear = [
        c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
        for c in channels
    ]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast(fg: str, bg: str) -> float:
    lf, lb = lum(fg), lum(bg)
    hi, lo = max(lf, lb), min(lf, lb)
    return (hi + 0.05) / (lo + 0.05)


# (前景, 背景, 最低要求) —— 浅色与深色两套主题的关键配对
PAIRS = [
    ("#171D1C", "#F5FAF8", 4.5),   # 浅色 onSurface / surface
    ("#3F4947", "#E9EFED", 4.5),   # 浅色 onSurfaceVariant / surfaceContainer(导航栏)
    ("#3F4947", "#EFF5F3", 4.5),   # 浅色 onSurfaceVariant / surfaceContainerLow(分组卡)
    ("#FFFFFF", "#0F766E", 4.5),   # 浅色 onPrimary / primary(月度汇总卡)
    ("#B3261E", "#F5FAF8", 4.5),   # 浅色 支出红 / surface
    ("#0F6E4A", "#F5FAF8", 4.5),   # 浅色 收入绿 / surface
    ("#DEE4E2", "#0E1514", 4.5),   # 深色 onSurface / surface
    ("#BEC9C6", "#1B2120", 4.5),   # 深色 onSurfaceVariant / surfaceContainer(导航栏)
    ("#BEC9C6", "#171D1C", 4.5),   # 深色 onSurfaceVariant / surfaceContainerLow(分组卡)
    ("#003732", "#86D5CC", 4.5),   # 深色 onPrimary / primary(月度汇总卡)
    ("#FFB4AB", "#0E1514", 4.5),   # 深色 支出红 / surface
    ("#6FDCA4", "#0E1514", 4.5),   # 深色 收入绿 / surface
]


def main() -> int:
    failed = 0
    for fg, bg, need in PAIRS:
        ratio = contrast(fg, bg)
        ok = ratio >= need
        if not ok:
            failed += 1
        print(f"{'PASS' if ok else 'FAIL'} {ratio:5.2f} (need {need})  {fg} on {bg}")

    print(f"\n{len(PAIRS) - failed}/{len(PAIRS)} passed"
          + ("" if failed == 0 else f"  —— {failed} 项不达标"))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
