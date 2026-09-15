# 拍照验收测试素材生成器:从固定图纸渲染一张"真实感"拼豆板照片
# (圆豆+中孔+亮度抖动+板底),并写出同源 PatternShare 图纸 JSON。
# 纯 ASCII;输出 board_8.png + chart_8.json 到脚本所在目录。
# 用法: powershell -File gen_board.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$size = 8
$cell = 100
$margin = 20
$dim = $size * $cell + 2 * $margin

# 图纸:花朵图案(点=空格;四角空,验证空格判定)
$rows = @(
    '........',
    '...RR...',
    '..RRRR..',
    '.RRYYRR.',
    '.RRYYRR.',
    '..RRRR..',
    '...RR...',
    '...GG...'
)
$palette = @(
    @{ code = 'R1'; name = 'Red';   rgb = 0xE4574C },
    @{ code = 'G1'; name = 'Green'; rgb = 0x3FA45B },
    @{ code = 'Y1'; name = 'Yellow'; rgb = 0xF2B33D }
)
$charIdx = @{ 'R' = 0; 'G' = 1; 'Y' = 2 }

# ---- 画板 ----
$bmp = New-Object System.Drawing.Bitmap($dim, $dim)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::FromArgb(0xE8, 0xE2, 0xD8))
$rand = New-Object System.Random(4242)

for ($y = 0; $y -lt $size; $y++) {
    for ($x = 0; $x -lt $size; $x++) {
        $ch = $rows[$y][$x]
        if ($ch -eq '.') { continue }
        $ci = $charIdx[[string]$ch]
        $cx = $margin + $x * $cell + [int]($cell / 2)
        $cy = $margin + $y * $cell + [int]($cell / 2)
        $rgbv = $palette[$ci].rgb
        $r = ($rgbv -shr 16) -band 0xFF
        $gc = ($rgbv -shr 8) -band 0xFF
        $b = $rgbv -band 0xFF
        # 每颗豆 ±5 亮度抖动,模拟真实光照与色差
        $j = $rand.Next(-5, 6)
        $r = [Math]::Max(0, [Math]::Min(255, $r + $j))
        $gc = [Math]::Max(0, [Math]::Min(255, $gc + $j))
        $b = [Math]::Max(0, [Math]::Min(255, $b + $j))
        $bead = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($r, $gc, $b))
        $hole = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(
            [int]($r * 0.55), [int]($gc * 0.55), [int]($b * 0.55)))
        $g.FillEllipse($bead, $cx - 42, $cy - 42, 84, 84)
        $g.FillEllipse($hole, $cx - 13, $cy - 13, 26, 26)
        $bead.Dispose(); $hole.Dispose()
    }
}
# 轻微全局光感:右上区域叠一层半透明白(PS5 的渐变构造器有坑,用矩形代替)
$light = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(20, 255, 255, 255))
$g.FillRectangle($light, 0, 0, $dim, [int]($dim / 2))
$light.Dispose()
$g.Dispose()
$outPng = Join-Path $PSScriptRoot 'board_8.png'
$bmp.Save($outPng, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output ("wrote " + $outPng)

# ---- 写同源图纸 JSON(PatternShare v1,RLE 行主序) ----
$rle = New-Object System.Text.StringBuilder
$runVal = -999; $runLen = 0
foreach ($row in $rows) {
    foreach ($chx in $row.ToCharArray()) {
        $v = -1
        if ($charIdx.ContainsKey([string]$chx)) { $v = $charIdx[[string]$chx] }
        if ($v -eq $runVal) { $runLen++ }
        else {
            if ($runLen -gt 0) { [void]$rle.Append("$runVal,$runLen,") }
            $runVal = $v; $runLen = 1
        }
    }
}
if ($runLen -gt 0) { [void]$rle.Append("$runVal,$runLen") }
else { $s = $rle.ToString().TrimEnd(','); $rle.Clear(); [void]$rle.Append($s) }

$colorsJson = ($palette | ForEach-Object {
    '{ "code": "' + $_.code + '", "name": "' + $_.name + '", "rgb": ' + $_.rgb + ' }'
}) -join ', '

$json = @"
{
  "format": "pindou-pattern",
  "version": 1,
  "app": "PindouPhoto",
  "name": "verify_selftest",
  "savedAt": 0,
  "cols": $size,
  "rows": $size,
  "round": false,
  "colors": [ $colorsJson ],
  "cells": [ $($rle.ToString()) ]
}
"@
$outJson = Join-Path $PSScriptRoot 'chart_8.json'
[IO.File]::WriteAllText($outJson, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("wrote " + $outJson)
