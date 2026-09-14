# Renders the Windows icon (desktop/icon/askya.ico) from the same geometry as
# the launcher icon: five petals, the app name along the right-hand petal, on
# the cream circle that Android shows through its launcher mask.
#
#   powershell -ExecutionPolicy Bypass -File tools\make-windows-icon.ps1
#
# Re-run it together with make-icon.ps1 whenever the flower or the word change.
# Small sizes (48 px and below) drop the word: it would render as a smudge, the
# same reason ic_flower.xml has no word.
#
# Keep this file ASCII-only: Windows PowerShell 5.1 reads BOM-less .ps1 in the
# system codepage.

Add-Type -AssemblyName System.Drawing

$out = Join-Path $PSScriptRoot '..\desktop\icon'
New-Item -ItemType Directory -Force $out | Out-Null
$ico = Join-Path (Resolve-Path $out) 'askya.ico'

$petalColor = [System.Drawing.ColorTranslator]::FromHtml('#EE8B3D')
$textColor = [System.Drawing.ColorTranslator]::FromHtml('#FFFFFF')
$bgColor = [System.Drawing.ColorTranslator]::FromHtml('#FBF3E7')

# --- the word as outlines, placed in the 108x108 icon canvas (as in make-icon.ps1) ---
$family = New-Object System.Drawing.FontFamily('Gabriola')
$word = New-Object System.Drawing.Drawing2D.GraphicsPath
$word.AddString('Askya', $family, [int][System.Drawing.FontStyle]::Regular, 200.0,
                (New-Object System.Drawing.PointF(0, 0)),
                [System.Drawing.StringFormat]::GenericTypographic)
$b = $word.GetBounds()
$m = New-Object System.Drawing.Drawing2D.Matrix
$m.Translate(69.5, 54.0)
$m.Scale((25.0 / $b.Width), (25.0 / $b.Width))
$m.Translate((-($b.X + $b.Width / 2)), (-($b.Y + $b.Height / 2)))
$word.Transform($m)

function Render([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    # The launcher shows the inner 72dp circle of the 108dp canvas; that circle
    # becomes the whole Windows icon.
    $k = $size / 72.0
    $toIcon = New-Object System.Drawing.Drawing2D.Matrix
    $toIcon.Scale($k, $k)
    $toIcon.Translate(-18, -18)

    $bg = New-Object System.Drawing.SolidBrush($bgColor)
    $g.FillEllipse($bg, 0, 0, ($size - 1), ($size - 1))

    $petal = New-Object System.Drawing.SolidBrush($petalColor)
    foreach ($deg in (18, 90, 162, 234, 306)) {
        $p = New-Object System.Drawing.Drawing2D.GraphicsPath
        $p.AddBezier(54, 54, 40, 47, 37, 30, 54, 20)
        $p.AddBezier(54, 20, 71, 30, 68, 47, 54, 54)
        $p.CloseFigure()
        $mm = $toIcon.Clone()
        $mm.RotateAt($deg, (New-Object System.Drawing.PointF(54, 54)))
        $p.Transform($mm)
        $g.FillPath($petal, $p)
        $p.Dispose()
    }

    if ($size -gt 48) {
        $tp = $word.Clone()
        $tp.Transform($toIcon)
        $g.FillPath((New-Object System.Drawing.SolidBrush($textColor)), $tp)
        $tp.Dispose()
    }
    $g.Dispose()

    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    return ,$ms.ToArray()
}

# --- ICO: header, one directory entry per size, then the PNG images ---
$sizes = @(256, 128, 64, 48, 32, 24, 16)
$images = @()
foreach ($s in $sizes) { $images += ,(Render $s) }

$fs = [System.IO.File]::Create($ico)
$w = New-Object System.IO.BinaryWriter($fs)
$w.Write([UInt16]0); $w.Write([UInt16]1); $w.Write([UInt16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $s = $sizes[$i]
    $dim = if ($s -ge 256) { 0 } else { $s }
    $w.Write([byte]$dim); $w.Write([byte]$dim); $w.Write([byte]0); $w.Write([byte]0)
    $w.Write([UInt16]1); $w.Write([UInt16]32)
    $w.Write([UInt32]$images[$i].Length); $w.Write([UInt32]$offset)
    $offset += $images[$i].Length
}
foreach ($img in $images) { $w.Write($img) }
$w.Close()

$word.Dispose()
Write-Output "Windows icon: $ico"
