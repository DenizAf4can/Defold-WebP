local M = {}

M.MSG_SET = hash("set_opacity")
M.MSG_GET = hash("get_opacity")
M.MSG_FADE = hash("fade_opacity")
M.MSG_SET_TARGETS = hash("set_opacity_targets")
M.MSG_OPACITY = hash("opacity")

local DEFAULT_EASING = go.EASING_LINEAR

function M.clamp(value)
    value = tonumber(value) or 1
    if value < 0 then
        return 0
    end
    if value > 1 then
        return 1
    end
    return value
end

local function to_url(target)
    if type(target) == "string" then
        return msg.url(target)
    end
    return target
end

function M.component_url(go_id, component_id)
    return msg.url(nil, go_id or ".", component_id or "sprite")
end

function M.sprite(go_id, component_id)
    return M.component_url(go_id, component_id or "sprite")
end

function M.set(target, opacity)
    local url = to_url(target)
    local alpha = M.clamp(opacity)
    local ok, tint = pcall(go.get, url, "tint")

    if ok and tint then
        tint.w = alpha
        go.set(url, "tint", tint)
        return true
    end

    return false
end

function M.get(target)
    local url = to_url(target)
    local ok, tint = pcall(go.get, url, "tint")

    if ok and tint then
        return M.clamp(tint.w)
    end

    return nil
end

function M.fade(target, opacity, duration, easing, delay, complete_function)
    local url = to_url(target)
    local alpha = M.clamp(opacity)
    duration = tonumber(duration) or 0
    delay = tonumber(delay) or 0

    local ok = pcall(go.animate, url, "tint.w", go.PLAYBACK_ONCE_FORWARD,
        alpha, easing or DEFAULT_EASING, duration, delay, complete_function)

    return ok
end

function M.set_many(targets, opacity)
    local applied = 0

    for _, target in ipairs(targets or {}) do
        if M.set(target, opacity) then
            applied = applied + 1
        end
    end

    return applied
end

function M.fade_many(targets, opacity, duration, easing, delay, complete_function)
    local applied = 0

    for _, target in ipairs(targets or {}) do
        if M.fade(target, opacity, duration, easing, delay, complete_function) then
            applied = applied + 1
        end
    end

    return applied
end

function M.set_go(go_id, component_ids, opacity)
    local targets = {}

    for _, component_id in ipairs(component_ids or { "sprite" }) do
        targets[#targets + 1] = M.component_url(go_id or ".", component_id)
    end

    return M.set_many(targets, opacity)
end

return M
