# Re-fetch thumbnails for the failed files: batch-request imageinfo with
# iiurlwidth=1280 (returns a proper /thumb/ path), download, keep 20KB..3MB.
# Reads titles from CREDITS.md lines like "- **File:xxx** - artist - license - url".
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$proxy = 'http://127.0.0.1:7890'
$dir = 'F:\delete\PDAPP\qa\verify_data\real'

$titles = @()
foreach ($line in [IO.File]::ReadAllLines('F:\delete\PDAPP\qa\verify_data\CREDITS.md')) {
    if ($line -match '^- \*\*(.+?)\*\*') { $titles += $Matches[1] }
}
Write-Output ('titles: ' + $titles.Count)

# batch in groups of 20
$info = @{}
for ($i = 0; $i -lt $titles.Count; $i += 20) {
    $grp = $titles[$i..([Math]::Min($i + 19, $titles.Count - 1))]
    $t = ($grp | ForEach-Object { $_.Replace(' ', '_') }) -join '|'
    $u = 'https://commons.wikimedia.org/w/api.php?action=query&titles=' +
         [uri]::EscapeDataString($t) +
         '&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&iiurlwidth=1280&format=json'
    $o = Join-Path $env:TEMP ('batch_' + $i + '.json')
    curl.exe -sS --max-time 60 -x $proxy -o $o $u
    $j = [IO.File]::ReadAllText($o, [Text.Encoding]::UTF8) | ConvertFrom-Json
    if ($j.query -and $j.query.pages) {
        foreach ($p in $j.query.pages.PSObject.Properties.Value) {
            if ($p.imageinfo -and $p.imageinfo[0].thumburl) {
                $info[$p.title] = $p.imageinfo[0].thumburl
            }
        }
    }
}
Write-Output ('thumburls: ' + $info.Count)

# download with proxy, keep 20KB..3MB
$n = 0
foreach ($t in $titles) {
    if (-not $info.ContainsKey($t)) { continue }
    $n++
    $slug = ($t -replace '^File:', '') -replace '[^A-Za-z0-9._-]', '_'
    if ($slug.Length -gt 60) { $slug = $slug.Substring(0, 60) }
    $out = Join-Path $dir ('{0:D2}_{1}.jpg' -f $n, $slug)
    curl.exe -sS --max-time 120 -x $proxy -o $out $info[$t]
    $len = (Get-Item $out).Length
    if ($len -lt 20480) {
        Remove-Item $out
        Write-Output ('skip small: ' + $t)
    } elseif ($len -gt 3MB) {
        # downscale to 1600px wide JPEG q85
        Add-Type -AssemblyName System.Drawing
        $img = [System.Drawing.Image]::FromFile($out)
        $s = 1600 / $img.Width
        $nb = New-Object System.Drawing.Bitmap(1600, [int]($img.Height * $s))
        $g = [System.Drawing.Graphics]::FromImage($nb)
        $g.InterpolationMode = 'HighQualityBicubic'
        $g.DrawImage($img, 0, 0, 1600, [int]($img.Height * $s))
        $g.Dispose(); $img.Dispose()
        $enc = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
               Where-Object { $_.MimeType -eq 'image/jpeg' }
        $ep = New-Object System.Drawing.Imaging.EncoderParameters(1)
        $ep.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter(
            [System.Drawing.Imaging.Encoder]::Quality, 85L)
        $nb.Save($out, $enc, $ep)
        $nb.Dispose()
        Write-Output ('downscaled: ' + $out)
    }
}
Write-Output 'done'
