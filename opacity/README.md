# Defold Opacity Helper

> You wanted opacity on Sprites and Game Objects?
> Fine. I made a tiny helper. It's not like alpha blending is hard or anything.

Defold Sprites already expose opacity through `tint.w`. This helper gives you a
small Lua API and an optional GO controller script.

## Script API

```lua
local opacity = require "opacity.opacity"

opacity.set("#sprite", 0.5)
opacity.fade("#sprite", 1.0, 0.25, go.EASING_INOUTSINE)
opacity.set_go(".", { "body", "eyes", "weapon" }, 0.35)
```

## GO Controller

Add `/opacity/opacity.script` to a Game Object, assign `target_1` ... `target_8`
to Sprite component URLs, then send messages:

```lua
msg.post("#opacity", "set_opacity", { opacity = 0.5 })
msg.post("#opacity", "fade_opacity", { to = 1.0, duration = 0.25 })
msg.post("#opacity", "get_opacity")
```

That is it. A polite little alpha switch. Hmph.
