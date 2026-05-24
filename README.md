# Defold WebP Import

> Hmph. So Defold wanted PNGs only, and you wanted WebP.
> Fine. I made them get along. D-don't misunderstand, I only did it because the
> texture pipeline looked lonely.

**Defold WebP Import** is a Defold `1.12.4+` library extension that lets the
editor and bob use `.webp` as a source image format.

It supports static WebP, alpha WebP, lossy WebP, lossless WebP, and animated
WebP. Final runtime output is still normal Defold texture data, so your texture
profiles stay in charge.

## Defold Library

The dependency exposes these library folders through `game.project`:

```ini
[library]
include_dirs = webp_import opacity
defold_min_version = 1.12.4
```

- `webp_import/` - editor and bob WebP import support.
- `opacity/` - small Lua helper for Sprite and GO-group opacity.

## What It Does

- Shows `.webp` files as image resources in the editor.
- Allows `.webp` in Atlas, Tile Source, Sprite, and GUI texture resource fields.
- Lets `.webp` files be dragged into compatible image/texture fields.
- Compiles standalone `.webp` files to `.texturec`.
- Expands animated `.webp` files into Defold texture-set animations.
- Expands animated `.webp` files inside Atlas as virtual frames.
- Adds `WebP -> Export Selected WebP Frames` for optional PNG frame export.
- Uses TwelveMonkeys `imageio-webp` `3.13.1` for editor and bob decoding.

## Install

Add the release archive to `game.project`:

```ini
[project]
dependencies#0 = https://github.com/DenizAf4can/Defold-WebP/archive/refs/tags/v.0.2.zip
```

Then run:

```text
Project -> Fetch Libraries
Project -> Reload Editor Scripts
```

## Use WebP

Use `.webp` anywhere you would normally pick an image source:

```text
/assets/player_idle.webp
/assets/player_run.webp
```

Animated WebP files become texture-set animations. If an animated WebP is placed
in an Atlas, it is expanded virtually as frames like:

```text
player_run.webp#frame-01
player_run.webp#frame-02
```

No PNG files are created unless you explicitly use:

```text
WebP -> Export Selected WebP Frames
```

## Opacity Helper

For one Sprite:

```lua
local opacity = require "opacity.opacity"

opacity.set("#sprite", 0.5)
opacity.fade("#sprite", 1.0, 0.25, go.EASING_INOUTSINE)
```

For a GO with several Sprite components:

```lua
opacity.set_go(".", { "body", "eyes", "weapon" }, 0.35)
```

Or add `/opacity/opacity.script` and control it by message:

```lua
msg.post("#opacity", "set_opacity", { opacity = 0.5 })
msg.post("#opacity", "fade_opacity", { to = 1.0, duration = 0.25 })
```

## Notes

- WebP is a source asset format, not a runtime compression backend.
- Animated WebP frame timings are averaged into one Defold `fps` value.

There. WebP in Defold.

Not that I did it because you asked nicely or a-anything!
