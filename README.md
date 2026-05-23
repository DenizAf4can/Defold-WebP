# Defold WebP Import

> B-baka... Defold'a WebP koymak icin koca engine fork'u mu dusundun?
> Neyse. Ben hallettim. Ama sakin yanlis anlama, sadece texture pipeline'in aglamasina dayanamadim.
> H-her neyse! Bu eklenti AI tarafından QOL Feature oldugunu dusundugumden dolayi gelistirildi.

**Defold WebP Import** is a library extension for Defold `1.12.4+` that lets
the editor and bob understand `.webp` as a source image format.

Static WebP, alpha WebP, lossy WebP, lossless WebP, and animated WebP are decoded
at import/build time, then handed back to Defold's normal texture pipeline.
Runtime output is still regular Defold texture data. Neat, tidy, no engine fork.

## What You Get

- `.webp` files appear as image resources in the Defold editor.
- Atlas image lists can point to `.webp`.
- Tile Source image fields can point to `.webp`.
- Standalone `.webp` files compile to `.texturec`.
- Animated `.webp` files compile to Defold texture sets and can be used directly
  from sprite/gui texture fields.
- Bob and the editor use the same WebP reader, powered by TwelveMonkeys
  `imageio-webp` `3.13.1`.

If you are testing locally, copy the `webp_import/` folder into your project.
Yes, that is the whole spell. Very dramatic.

## Use It

### Atlas

Add WebP files to an Atlas:

```text
example_idle.webp
example_run.webp
```

If a WebP is animated, its frames are expanded into the generated texture set.
The default animation id is the WebP base filename.

### Tile Source

Set the Tile Source image to a `.webp` file:

```text
image: "/tiles/forest_tiles.webp"
```

For animated WebP tile sources, frames are laid out as a horizontal tile sheet.
If the Tile Source has no animations, one is created automatically from the WebP
frames.

### Standalone Texture

Reference a `.webp` directly from sprite/gui texture fields. It will compile to:

```text
.texturec
```

Animated WebP additionally produces:

```text
.a.texturesetc
```

## Animated WebP Notes

Defold texture set animations use a single `fps` value. WebP can store different
durations per frame. This extension averages the WebP frame delays and converts
that into one Defold `fps`.

So if your animated WebP has wildly different frame durations, it will still
work, but it may feel a little more disciplined than the original. Hmph. Order
is important.

## What This Is Not

This extension is not:

- a runtime Lua WebP decoder
- a WebP texture compression backend
- an engine fork
- a replacement for texture profiles

WebP is only the source format. Final runtime texture output is still decided by
Defold texture profiles and texture compression settings.

## Third Party

This extension bundles TwelveMonkeys ImageIO jars. See:

- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys)

## Tiny Troubleshooting Corner

**My `.webp` imports but only one frame plays.**

Make sure you are referencing the animated `.webp` as a texture set source, for
example from a sprite/gui texture field, or placing it inside an Atlas. A plain
`.texturec` is still a single texture image.

**Frame timing feels slightly different.**

That is expected for variable-duration WebP animations. Defold stores animation
speed as one `fps`, so the extension averages the source frame delays.

**The editor cannot preview WebP.**

Check that all jars in `webp_import/plugins/share/` are present. The editor and
bob both need the TwelveMonkeys jars.

## Final Word

There. WebP in Defold, politely domesticated.

Not that I did it because you asked nicely or anything. Hmph!

