# Parse the 3 Commons API responses, pick photo candidates, and emit:
#   real/download_list.txt  (url|filename per line, thumbnails at 1600px)
#   credits_body.md         (title/artist/license/source per file)
# ASCII only. No network here - downloads run via curl from the list.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$files = @('c1b.json', 'c2.json', 'c3.json')
$outDir = 'F:\delete\PDAPP\qa\verify_data\real'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

$seen = @{}
$list = New-Object System.Text.StringBuilder
$credits = New-Object System.Text.StringBuilder
$n = 0

foreach ($f in $files) {
    $path = Join-Path $env:TEMP $f
    if (-not (Test-Path $path)) { continue }
    $j = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8) | ConvertFrom-Json
    if (-not $j.query -or -not $j.query.pages) { continue }
    $pages = $j.query.pages.PSObject.Properties.Value
    foreach ($p in $pages) {
        if ($n -ge 30) { break }
        $ii = $p.imageinfo[0]
        if (-not $ii) { continue }
        $title = $p.title
        if ($seen.ContainsKey($title)) { continue }
        $lower = $title.ToLower()
        if (-not ($lower.EndsWith('.jpg') -or $lower.EndsWith('.jpeg') -or $lower.EndsWith('.png'))) { continue }
        if ($ii.width -lt 600 -or $ii.height -lt 400) { continue }
        $url = $ii.thumburl
        if (-not $url) { $url = $ii.url }
        $url = ($url -split '\?')[0]
        $ext = '.jpg'
        if ($lower.EndsWith('.png')) { $ext = '.png' }
        $seen[$title] = $true
        $n++
        $slug = ($title -replace '^File:', '') -replace '[^A-Za-z0-9._-]', '_'
        if ($slug.Length -gt 60) { $slug = $slug.Substring(0, 60) }
        $name = ('{0:D2}_{1}{2}' -f $n, $slug, $ext)

        $meta = $ii.extmetadata
        $artist = ''
        if ($meta.Artist -and $meta.Artist.value) {
            $artist = ([regex]::Replace($meta.Artist.value, '<[^>]+>', '')).Trim()
        }
        if ($artist.Length -gt 80) { $artist = $artist.Substring(0, 80) }
        $lic = ''
        if ($meta.LicenseShortName -and $meta.LicenseShortName.value) {
            $lic = $meta.LicenseShortName.value
        }
        [void]$list.AppendLine(($url + '|' + $name))
        [void]$credits.AppendLine(('- **' + $title + '** - ' + $artist + ' - ' + $lic + ' - ' + $ii.descriptionurl))
        [void]$credits.AppendLine(('  file: real/' + $name))
    }
    if ($n -ge 30) { break }
}

[IO.File]::WriteAllText((Join-Path $outDir 'download_list.txt'), $list.ToString(), (New-Object System.Text.UTF8Encoding($false)))
$head = "# Photo-check review set - real board photos`n`nSource: Wikimedia Commons (searches: perler beads / hama beads / fuse beads).`nThumbnails at 1600px. Licenses as noted; for in-repo manual review only.`n`n"
[IO.File]::WriteAllText('F:\delete\PDAPP\qa\verify_data\CREDITS.md', $head + $credits.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Output ('candidates: ' + $n)
