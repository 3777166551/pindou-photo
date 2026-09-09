# Generate boundary/fuzz test images into qa/test_data/.
# ASCII-only script. Outputs (all tiny PNG payloads except big one):
#   fuzz_tiny.png   8x8 flat
#   fuzz_big.png    2200x2200 flat regions (compresses well)
#   fuzz_alpha.png  300x300 transparent background + opaque disc
#   fuzz_gray.png   300x300 grayscale ramp-ish flat patches
#   fuzz_trunc.png  ci_photo.png truncated to 30% bytes (corrupt)
#   fuzz_zero.png   0 bytes (invalid image)
Add-Type -AssemblyName System.Drawing
$dir = $PSScriptRoot

function Save-Bmp($bmp, $name) {
    $out = Join-Path $dir $name
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output ("saved: " + $name)
}

# 8x8 tiny
$b = New-Object System.Drawing.Bitmap(8, 8)
$g = [System.Drawing.Graphics]::FromImage($b)
$g.Clear([System.Drawing.Color]::FromArgb(255, 200, 40, 40))
$g.Dispose()
Save-Bmp $b 'fuzz_tiny.png'

# 2200x2200 big, flat regions (PNG compresses flat areas to a small file)
$b = New-Object System.Drawing.Bitmap(2200, 2200)
$g = [System.Drawing.Graphics]::FromImage($b)
$g.Clear([System.Drawing.Color]::FromArgb(255, 240, 236, 228))
$r = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 190, 60, 50))
$g.FillEllipse($r, 100, 100, 900, 900)
$bl = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 50, 90, 170))
$g.FillRectangle($bl, 1300, 200, 700, 700)
$g.Dispose()
Save-Bmp $b 'fuzz_big.png'

# 300x300 with transparent background + opaque disc
$b = New-Object System.Drawing.Bitmap(300, 300)
$b.MakeTransparent()
$g = [System.Drawing.Graphics]::FromImage($b)
$gr = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 40, 160, 90))
$g.FillEllipse($gr, 60, 60, 180, 180)
$g.Dispose()
Save-Bmp $b 'fuzz_alpha.png'

# 300x300 grayscale patches
$b = New-Object System.Drawing.Bitmap(300, 300)
$g = [System.Drawing.Graphics]::FromImage($b)
$g.Clear([System.Drawing.Color]::FromArgb(255, 20, 20, 20))
$w = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 235, 235, 235))
$g.FillRectangle($w, 40, 40, 220, 220)
$m = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 128, 128, 128))
$g.FillRectangle($m, 90, 90, 120, 120)
$g.Dispose()
Save-Bmp $b 'fuzz_gray.png'

# truncated (corrupt) PNG: first 30% of ci_photo.png bytes
$src = [IO.File]::ReadAllBytes((Join-Path $dir 'ci_photo.png'))
$cut = New-Object byte[] ([int]($src.Length * 0.3))
[Array]::Copy($src, $cut, $cut.Length)
[IO.File]::WriteAllBytes((Join-Path $dir 'fuzz_trunc.png'), $cut)
Write-Output 'saved: fuzz_trunc.png'

# zero-byte png
[IO.File]::WriteAllBytes((Join-Path $dir 'fuzz_zero.png'), (New-Object byte[] 0))
Write-Output 'saved: fuzz_zero.png'
