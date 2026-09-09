# Generate the deterministic CI smoke-test photo (flat color regions).
# ASCII-only script; output: qa/test_data/ci_photo.png (480x480 PNG).
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap(480, 480)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None

# warm cream background: big flat area -> single color >= 500 beads at 58x58
$bg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(242, 237, 228))
$g.FillRectangle($bg, 0, 0, 480, 480)

$red = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(211, 47, 47))
$g.FillEllipse($red, 40, 60, 200, 200)

$blue = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(30, 107, 184))
$g.FillRectangle($blue, 280, 40, 150, 150)

$green = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(46, 125, 50))
$tri = @([System.Drawing.Point]::new(60, 420), [System.Drawing.Point]::new(190, 420), [System.Drawing.Point]::new(125, 300))
$g.FillPolygon($green, $tri)

$purple = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(123, 31, 162))
$star = @([System.Drawing.Point]::new(360, 260), [System.Drawing.Point]::new(390, 340), [System.Drawing.Point]::new(430, 350), [System.Drawing.Point]::new(395, 375), [System.Drawing.Point]::new(420, 440), [System.Drawing.Point]::new(355, 395), [System.Drawing.Point]::new(300, 440), [System.Drawing.Point]::new(320, 375), [System.Drawing.Point]::new(290, 350), [System.Drawing.Point]::new(330, 340))
$g.FillPolygon($purple, $star)

$g.Dispose()
$out = Join-Path $PSScriptRoot 'ci_photo.png'
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "saved: $out"
