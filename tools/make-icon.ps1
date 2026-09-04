# Regenerates the launcher icon vectors.
#
# VectorDrawable cannot draw text, so the app name is converted to outline paths
# from the Gabriola font (Windows). Re-run this after changing the wording,
# the font, or the flower geometry:
#
#   powershell -ExecutionPolicy Bypass -File tools\make-icon.ps1
#
# Keep this file ASCII-only: Windows PowerShell 5.1 reads BOM-less .ps1 in the
# system codepage and would mangle non-ASCII literals.

Add-Type -AssemblyName System.Drawing

$res = Join-Path $PSScriptRoot '..\app\src\main\res' | Resolve-Path

# --- app name as outlines, already placed in the 108x108 icon canvas ---

$word = 'Askya'
$fontName = 'Gabriola'
# The word runs outward from the flower centre along the right-hand petal.
# That petal points right, so its axis is horizontal and the word needs no rotation.
$targetCx = 69.5   # pulled toward the centre: the petal narrows sharply near the tip
$targetCy = 54.0
$targetW = 25.0
$rotation = 0.0

$family = New-Object System.Drawing.FontFamily($fontName)
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$path.AddString($word, $family, [int][System.Drawing.FontStyle]::Regular, 200.0,
                (New-Object System.Drawing.PointF(0, 0)),
                [System.Drawing.StringFormat]::GenericTypographic)

$b = $path.GetBounds()
$m = New-Object System.Drawing.Drawing2D.Matrix
$m.Translate($targetCx, $targetCy)
$m.Rotate($rotation)
$m.Scale(($targetW / $b.Width), ($targetW / $b.Width))
$m.Translate((-($b.X + $b.Width / 2)), (-($b.Y + $b.Height / 2)))
$path.Transform($m)

$pts = $path.PathPoints
$types = $path.PathTypes
$sb = New-Object System.Text.StringBuilder
$ci = [System.Globalization.CultureInfo]::InvariantCulture
function N($v) { [math]::Round($v, 2).ToString($ci) }

# GraphicsPath -> SVG/VectorDrawable path data. Bezier segments carry three
# points each, so the index advances by two extra on those.
function Convert-PathData($graphicsPath) {
    $p = $graphicsPath.PathPoints
    $t = $graphicsPath.PathTypes
    $out = New-Object System.Text.StringBuilder
    $j = 0
    while ($j -lt $p.Length) {
        switch ($t[$j] -band 0x07) {
            0 { [void]$out.Append("M$(N $p[$j].X),$(N $p[$j].Y)") }
            1 { [void]$out.Append("L$(N $p[$j].X),$(N $p[$j].Y)") }
            3 {
                [void]$out.Append("C$(N $p[$j].X),$(N $p[$j].Y) $(N $p[$j+1].X),$(N $p[$j+1].Y) $(N $p[$j+2].X),$(N $p[$j+2].Y)")
                $j += 2
            }
        }
        if (($t[$j] -band 0x80) -ne 0) { [void]$out.Append('Z') }
        [void]$out.Append(' ')
        $j++
    }
    return $out.ToString().Trim()
}

$textPath = Convert-PathData $path

# --- the same word as a standalone wordmark for the drawer ---
#
# The font is not available on Android and cannot be bundled, so the wordmark
# ships as outlines too. Colour is plain black: the drawable is tinted at the
# call site, which keeps it in step with the theme.

# Начертание жирное: у Gabriola нет отдельного bold-файла, GDI+ синтезирует его
# утолщением контура — для вордмарка этого достаточно, а на иконке слово мелкое
# и остаётся обычным.
$markPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$markPath.AddString($word, $family, [int][System.Drawing.FontStyle]::Bold, 200.0,
                    (New-Object System.Drawing.PointF(0, 0)),
                    [System.Drawing.StringFormat]::GenericTypographic)
$mb = $markPath.GetBounds()
$mm = New-Object System.Drawing.Drawing2D.Matrix
$mm.Translate((-$mb.X), (-$mb.Y))
$markPath.Transform($mm)

$markHeightDp = 34
$markWidthDp = [math]::Round($markHeightDp * $mb.Width / $mb.Height)

$wordmark = @"
<?xml version="1.0" encoding="utf-8"?>
<!-- @MARKDOC@ -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${markWidthDp}dp"
    android:height="${markHeightDp}dp"
    android:viewportWidth="$(N $mb.Width)"
    android:viewportHeight="$(N $mb.Height)">

    <path
        android:fillColor="#FF000000"
        android:pathData="$(Convert-PathData $markPath)" />

</vector>
"@

# Превью вордмарка на фоне меню — проверять правку по устройству мешают кэши.
$markW = 400
$markH = [int][math]::Round($markW * $mb.Height / $mb.Width)
$markBmp = New-Object System.Drawing.Bitmap($markW, ($markH + 40))
$markG = [System.Drawing.Graphics]::FromImage($markBmp)
$markG.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$markG.Clear([System.Drawing.ColorTranslator]::FromHtml('#F0EEE6'))
$markScale = New-Object System.Drawing.Drawing2D.Matrix
$markScale.Translate(0, 20)
$markScale.Scale(($markW / $mb.Width), ($markW / $mb.Width))
$markPreviewPath = $markPath.Clone()
$markPreviewPath.Transform($markScale)
$markG.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml('#1F1E1B'))), $markPreviewPath)
$markG.Dispose()
$markPreviewPath.Dispose()
$markBmp.Save((Join-Path $PSScriptRoot 'wordmark-preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$markBmp.Dispose()

$markPath.Dispose()

# --- the whole name as one calligraphic wordmark ---
#
# AskyaEcho shows the played part of a track with this word instead of a slider,
# and the same word rides the petal in the opening animation, where only the
# "Askya" half is on screen until the flower stops turning. Both need the two
# halves in one coordinate space, so the name is a single path with a published
# split point rather than two files that would have to be lined up by hand.

$echoMark = New-Object System.Drawing.Drawing2D.GraphicsPath
$echoMark.AddString('AskyaEcho', $family, [int][System.Drawing.FontStyle]::Bold, 200.0,
                    (New-Object System.Drawing.PointF(0, 0)),
                    [System.Drawing.StringFormat]::GenericTypographic)
$ebRaw = $echoMark.GetBounds()

# The split is measured on the ink, not on the pen advance: Gabriola finishes
# "Askya" with a swash that runs past the pen, and a cut there would slice
# through the tail. Halfway between the last ink of "Askya" and the first ink
# of "Echo" both sides are empty.
$askyaInk = New-Object System.Drawing.Drawing2D.GraphicsPath
$askyaInk.AddString('Askya', $family, [int][System.Drawing.FontStyle]::Bold, 200.0,
                    (New-Object System.Drawing.PointF(0, 0)),
                    [System.Drawing.StringFormat]::GenericTypographic)
$aib = $askyaInk.GetBounds()
$echoInk = New-Object System.Drawing.Drawing2D.GraphicsPath
$echoInk.AddString('Echo', $family, [int][System.Drawing.FontStyle]::Bold, 200.0,
                   (New-Object System.Drawing.PointF(0, 0)),
                   [System.Drawing.StringFormat]::GenericTypographic)
$eib = $echoInk.GetBounds()

$splitX = ((($aib.X + $aib.Width) + (($ebRaw.X + $ebRaw.Width) - $eib.Width)) / 2)
$echoSplit = [math]::Round((($splitX - $ebRaw.X) / $ebRaw.Width), 4)
$askyaShare = [math]::Round((($aib.X + $aib.Width - $ebRaw.X) / $ebRaw.Width), 4)

$em = New-Object System.Drawing.Drawing2D.Matrix
$em.Translate((-$ebRaw.X), (-$ebRaw.Y))
$echoMark.Transform($em)

$echoHeightDp = 34
$echoWidthDp = [math]::Round($echoHeightDp * $ebRaw.Width / $ebRaw.Height)

$echoWordmark = @"
<?xml version="1.0" encoding="utf-8"?>
<!-- @ECHODOC@ -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${echoWidthDp}dp"
    android:height="${echoHeightDp}dp"
    android:viewportWidth="$(N $ebRaw.Width)"
    android:viewportHeight="$(N $ebRaw.Height)">

    <path
        android:fillColor="#FF000000"
        android:pathData="$(Convert-PathData $echoMark)" />

</vector>
"@

# Where the word sits on the petal: the icon places "Askya" there, and the
# animation keeps the same spot, so the frame the flower stops on and the
# launcher icon show the same thing.
$wordStart = [math]::Round((($targetCx - $targetW / 2) / 108.0), 4)
$wordFull = [math]::Round((($targetW / 108.0) / $askyaShare), 4)

# Preview of what the section animates: the flower before the word is finished,
# after it is finished, and the progress line half played.
$echoW = 760
$echoH = 560
$echoBmp = New-Object System.Drawing.Bitmap($echoW, $echoH)
$echoG = [System.Drawing.Graphics]::FromImage($echoBmp)
$echoG.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$echoG.Clear([System.Drawing.ColorTranslator]::FromHtml('#0B0A09'))
$sunsetBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml('#F08A3C'))
$mutedBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml('#2A2520'))
$whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)

function Draw-Flower($gfx, $x, $y, $side, $written) {
    $s = $side / 108.0
    $petalBrush2 = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml('#EE8B3D'))
    foreach ($deg in (18, 90, 162, 234, 306)) {
        $p = New-Object System.Drawing.Drawing2D.GraphicsPath
        $p.AddBezier(54, 54, 40, 47, 37, 30, 54, 20)
        $p.AddBezier(54, 20, 71, 30, 68, 47, 54, 54)
        $p.CloseFigure()
        $mtx = New-Object System.Drawing.Drawing2D.Matrix
        $mtx.Translate($x, $y)
        $mtx.Scale($s, $s)
        $mtx.RotateAt($deg, (New-Object System.Drawing.PointF(54, 54)))
        $p.Transform($mtx)
        $gfx.FillPath($petalBrush2, $p)
        $p.Dispose()
    }
    $petalBrush2.Dispose()

    $w = $side * $wordFull
    $k = $w / $ebRaw.Width
    $h = $ebRaw.Height * $k
    $left = $x + $side * $wordStart
    $top = $y + $side / 2.0 - $h / 2.0
    $shown = $w * ($echoSplit + (1.0 - $echoSplit) * $written)

    $wp = $echoMark.Clone()
    $wm = New-Object System.Drawing.Drawing2D.Matrix
    $wm.Translate($left, $top)
    $wm.Scale($k, $k)
    $wp.Transform($wm)
    $gfx.SetClip((New-Object System.Drawing.RectangleF($left, $top, $shown, $h)))
    $gfx.FillPath($whiteBrush, $wp)
    $gfx.ResetClip()
    $wp.Dispose()
}

Draw-Flower $echoG 40 30 300 0.0
Draw-Flower $echoG 400 30 300 1.0

# The progress line: the word filled with sunset up to the played part.
$barW = 660.0
$barK = $barW / $ebRaw.Width
$barH = $ebRaw.Height * $barK
$barX = 50.0
$barY = 350.0
$barPath = $echoMark.Clone()
$barM = New-Object System.Drawing.Drawing2D.Matrix
$barM.Translate($barX, $barY)
$barM.Scale($barK, $barK)
$barPath.Transform($barM)
$echoG.FillPath($mutedBrush, $barPath)
$echoG.SetClip((New-Object System.Drawing.RectangleF($barX, $barY, ($barW * 0.42), $barH)))
$echoG.FillPath($sunsetBrush, $barPath)
$echoG.ResetClip()
$barPath.Dispose()

$echoG.Dispose()
$echoPreview = Join-Path $PSScriptRoot 'echo-preview.png'
$echoBmp.Save($echoPreview, [System.Drawing.Imaging.ImageFormat]::Png)
$echoBmp.Dispose()
$askyaInk.Dispose()
$echoInk.Dispose()
$echoMark.Dispose()

# --- "new block" action icon: a pencil plus the calligraphic A ---
#
# Drawn on the 24x24 canvas Material icons use. The pencil sits in the upper
# right and the letter fills the lower left: overlapping them turns to mush at
# 24dp, while side by side both still read.

# Two pieces, not one: body and graphite tip with a hairline gap between them.
# A single silhouette at this length reads as a smudge; the gap is what makes
# the eye see a pencil. Slender on purpose — a short wide wedge looks like a
# blob at 24dp.
$pencilBody = 'M15.23,7.55 L20.81,1.97 L22.43,3.59 L16.85,9.17 Z'
$pencilTip = 'M14.98,7.80 L16.60,9.42 L13.53,10.87 Z'

$letterPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$letterPath.AddString('A', $family, [int][System.Drawing.FontStyle]::Bold, 200.0,
                      (New-Object System.Drawing.PointF(0, 0)),
                      [System.Drawing.StringFormat]::GenericTypographic)
$lb = $letterPath.GetBounds()
$letterTargetW = 14.0
$lm = New-Object System.Drawing.Drawing2D.Matrix
$lm.Translate(9.5, 15.0)
$lm.Scale(($letterTargetW / $lb.Width), ($letterTargetW / $lb.Width))
$lm.Translate((-($lb.X + $lb.Width / 2)), (-($lb.Y + $lb.Height / 2)))
$letterPath.Transform($lm)

$newBlock = @"
<?xml version="1.0" encoding="utf-8"?>
<!-- @ACTIONDOC@ -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">

    <path
        android:fillColor="#FF000000"
        android:pathData="$pencilBody" />

    <path
        android:fillColor="#FF000000"
        android:pathData="$pencilTip" />

    <path
        android:fillColor="#FF000000"
        android:pathData="$(Convert-PathData $letterPath)" />

</vector>
"@

# Превью в реальном размере кнопки: на 24dp читается далеко не всё, что
# выглядит хорошо в редакторе.
$actionSizes = @(96, 48, 24)
$actionStrip = New-Object System.Drawing.Bitmap(260, 140)
$actionG = [System.Drawing.Graphics]::FromImage($actionStrip)
$actionG.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$actionG.Clear([System.Drawing.ColorTranslator]::FromHtml('#F6E5DE'))
$actionBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml('#8A4B32'))
$ax = 20
foreach ($px in $actionSizes) {
    $k2 = $px / 24.0
    $m2 = New-Object System.Drawing.Drawing2D.Matrix
    $m2.Translate($ax, 20)
    $m2.Scale($k2, $k2)

    $p2 = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p2.AddPolygon(@(
        (New-Object System.Drawing.PointF(15.23, 7.55)),
        (New-Object System.Drawing.PointF(20.81, 1.97)),
        (New-Object System.Drawing.PointF(22.43, 3.59)),
        (New-Object System.Drawing.PointF(16.85, 9.17))
    ))
    $p2.StartFigure()
    $p2.AddPolygon(@(
        (New-Object System.Drawing.PointF(14.98, 7.80)),
        (New-Object System.Drawing.PointF(16.60, 9.42)),
        (New-Object System.Drawing.PointF(13.53, 10.87))
    ))
    $p2.Transform($m2)
    $actionG.FillPath($actionBrush, $p2)
    $p2.Dispose()

    $l2 = $letterPath.Clone()
    $l2.Transform($m2)
    $actionG.FillPath($actionBrush, $l2)
    $l2.Dispose()

    $ax += $px + 20
}
$actionG.Dispose()
$actionStrip.Save((Join-Path $PSScriptRoot 'new-block-preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$actionStrip.Dispose()
$letterPath.Dispose()

# --- flower geometry ---

$petal = 'M54,54 C40,47 37,30 54,20 C71,30 68,47 54,54 Z'

# Five identical petals. 90 degrees points right and carries the word;
# the tip sits 34 from the centre, inside the 36 safe radius of an adaptive icon.
$sides = ((18, 90, 162, 234, 306) | ForEach-Object {
@"
    <group
        android:pivotX="54"
        android:pivotY="54"
        android:rotation="$_">
        <path
            android:fillColor="#EE8B3D"
            android:pathData="$petal" />
    </group>
"@
}) -join "`r`n"

# --- flower on its own, without the lettering ---
#
# Нужен там, где Askya показывает себя мелко: на иконке слово вдоль лепестка
# читается, а в анимации ожидания превратилось бы в грязь.
#
# Лепестки завёрнуты в именованную группу: за неё цветок берёт системная
# заставка (ic_flower_breathing.xml) — дышать на ней может только сам вектор,
# Compose там ещё не запущен. Своих преобразований у группы нет, поэтому всем
# остальным она ничего не меняет.
$flower = @"
<?xml version="1.0" encoding="utf-8"?>
<!-- @FLOWERDOC@ -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <group
        android:name="breath"
        android:pivotX="54"
        android:pivotY="54">

$sides

    </group>

</vector>
"@

# --- the same flower, painted by the theme ---
#
# Only the Android 12+ system splash uses it. There the colour is chosen by the
# person in the settings, and a drawable cannot read a preference: the theme
# passes it in as ?attr/askyaFlowerInk, and MainActivity picks the theme.
#
# A second copy of the geometry is generated rather than hand-written for the
# same reason ic_flower.xml is generated at all: petals are edited here, and a
# hand-kept copy would drift on the first edit. ic_flower.xml itself keeps the
# literal colour — it is inflated by the launcher (widgets) and by SystemUI
# (notification icons), where our theme attribute does not exist.
$flowerSplash = @"
<?xml version="1.0" encoding="utf-8"?>
<!-- @SPLASHDOC@ -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <group
        android:name="breath"
        android:pivotX="54"
        android:pivotY="54">

$($sides -replace '#EE8B3D', '?attr/askyaFlowerInk')

    </group>

</vector>
"@

$foreground = @"
<?xml version="1.0" encoding="utf-8"?>
<!-- @DOC@ -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

$sides

    <path
        android:fillColor="#FFFFFF"
        android:pathData="$textPath" />

</vector>
"@

# No monochrome layer is generated on purpose: it exists only for Android 13+
# themed icons, where the launcher would recolour the silhouette to match the
# wallpaper and drop both the orange and the lettering. See mipmap-anydpi-v26.

$enc = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText("$res\drawable\ic_launcher_foreground.xml", $foreground, $enc)
[System.IO.File]::WriteAllText("$res\drawable\ic_wordmark.xml", $wordmark, $enc)
[System.IO.File]::WriteAllText("$res\drawable\ic_wordmark_echo.xml", $echoWordmark, $enc)
[System.IO.File]::WriteAllText("$res\drawable\ic_new_block.xml", $newBlock, $enc)
[System.IO.File]::WriteAllText("$res\drawable\ic_flower.xml", $flower, $enc)
[System.IO.File]::WriteAllText("$res\drawable\ic_flower_splash.xml", $flowerSplash, $enc)

Write-Output ('Regenerated ic_launcher_foreground.xml, ic_wordmark.xml, ic_wordmark_echo.xml, ' +
              'ic_new_block.xml, ic_flower.xml and ic_flower_splash.xml.')
Write-Output 'The @DOC@ / @MARKDOC@ / @ECHODOC@ / @ACTIONDOC@ / @FLOWERDOC@ / @SPLASHDOC@ placeholders are replaced by hand.'

# Numbers the animation is built on. Compose cannot read them out of the vector,
# so they live as constants in EchoCurtain.kt and are checked against this line
# whenever the word, the font or the petal geometry changes.
Write-Output "EchoCurtain.kt: WORD_SPLIT = $echoSplit, WORD_START = $wordStart, WORD_FULL = $wordFull"
Write-Output "Echo preview: $echoPreview"

# --- local preview ---
#
# Renders the same geometry with GDI+ so the icon can be checked without a device.
# Android and system icon caches hold on to the old bitmap for a long time, which
# makes on-device iteration slow and unreliable.

$petalColor = [System.Drawing.ColorTranslator]::FromHtml('#EE8B3D')
$textColor = [System.Drawing.ColorTranslator]::FromHtml('#FFFFFF')
$bgColor = [System.Drawing.ColorTranslator]::FromHtml('#FBF3E7')

$size = 432                  # xxxhdpi: the 108dp canvas at 4x
$k = $size / 108.0

$bmp = New-Object System.Drawing.Bitmap($size, $size)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear($bgColor)

# Launcher masks clip to roughly the inner 72dp circle; draw it to show what survives.
$petalBrush = New-Object System.Drawing.SolidBrush($petalColor)
foreach ($deg in (18, 90, 162, 234, 306)) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddBezier(54, 54, 40, 47, 37, 30, 54, 20)
    $p.AddBezier(54, 20, 71, 30, 68, 47, 54, 54)
    $p.CloseFigure()
    $mm = New-Object System.Drawing.Drawing2D.Matrix
    $mm.Scale($k, $k)
    $mm.RotateAt($deg, (New-Object System.Drawing.PointF(54, 54)))
    $p.Transform($mm)
    $g.FillPath($petalBrush, $p)
    $p.Dispose()
}

$tp = $path.Clone()
$ms = New-Object System.Drawing.Drawing2D.Matrix
$ms.Scale($k, $k)
$tp.Transform($ms)
$textBrush = New-Object System.Drawing.SolidBrush($textColor)
$g.FillPath($textBrush, $tp)
$tp.Dispose()

$maskPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(60, 0, 0, 0), 2)
$g.DrawEllipse($maskPen, (18 * $k), (18 * $k), (72 * $k), (72 * $k))

$g.Dispose()
$preview = Join-Path $PSScriptRoot 'icon-preview.png'
$bmp.Save($preview, [System.Drawing.Imaging.ImageFormat]::Png)

# Same icon at the sizes a launcher actually uses, to judge whether the word reads.
$strip = New-Object System.Drawing.Bitmap(400, 230)
$gs = [System.Drawing.Graphics]::FromImage($strip)
$gs.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$gs.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gs.Clear([System.Drawing.Color]::White)
$font = New-Object System.Drawing.Font('Segoe UI', 10)
$black = [System.Drawing.Brushes]::Black
$x = 20
foreach ($px in (192, 96, 48)) {
    $gs.DrawImage($bmp, $x, 20, $px, $px)
    $gs.DrawString("$px px", $font, $black, $x, (20 + $px + 4))
    $x += $px + 24
}
$gs.Dispose()
$font.Dispose()
$sizes = Join-Path $PSScriptRoot 'icon-preview-sizes.png'
$strip.Save($sizes, [System.Drawing.Imaging.ImageFormat]::Png)
$strip.Dispose()

$bmp.Dispose()
$path.Dispose()

Write-Output "Preview: $preview (thin circle marks the launcher mask)"
Write-Output "Real sizes: $sizes"
