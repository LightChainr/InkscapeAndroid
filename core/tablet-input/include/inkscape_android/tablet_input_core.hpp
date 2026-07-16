#pragma once

#include <cstdint>
#include <optional>
#include <unordered_set>
#include <vector>

namespace inkscape_android::input {

enum class DeviceKind {
    Pen,
    Eraser,
    Finger,
    Mouse,
    Unknown,
};

enum class PointerPhase {
    Down,
    Move,
    Up,
    Cancel,
};

enum class InputOwner {
    None,
    Pen,
    TouchPending,
    TouchTransform,
    BlockUntilAllTouchUp,
};

enum class RouterAction {
    Consume,
    ForwardPenDown,
    ForwardPenMove,
    ForwardPenUp,
    BeginTouchTransform,
    UpdateTouchTransform,
    EndTouchTransform,
    AbortInteraction,
};

struct PointerEvent {
    PointerPhase phase = PointerPhase::Move;
    DeviceKind device = DeviceKind::Unknown;
    std::int32_t pointer_id = -1;
    double x = 0.0;
    double y = 0.0;
};

struct RouterResult {
    InputOwner owner = InputOwner::None;
    std::vector<RouterAction> actions;

    [[nodiscard]] bool contains(RouterAction action) const;
};

class InputRouter {
public:
    [[nodiscard]] RouterResult handle(PointerEvent const &event);
    [[nodiscard]] RouterResult cancel_all();

    [[nodiscard]] InputOwner owner() const noexcept { return owner_; }
    [[nodiscard]] std::size_t active_touch_count() const noexcept { return touch_ids_.size(); }
    [[nodiscard]] bool pen_interaction_active() const noexcept { return pen_id_.has_value(); }

private:
    [[nodiscard]] RouterResult handle_pen(PointerEvent const &event);
    [[nodiscard]] RouterResult handle_touch(PointerEvent const &event);
    [[nodiscard]] RouterResult result(std::vector<RouterAction> actions) const;

    InputOwner owner_ = InputOwner::None;
    std::optional<std::int32_t> pen_id_;
    std::unordered_set<std::int32_t> ignored_pen_ids_;
    std::unordered_set<std::int32_t> touch_ids_;
};

enum class InteractionState {
    Idle,
    Armed,
    Active,
    Committed,
    Aborted,
};

class InteractionSession {
public:
    [[nodiscard]] bool arm();
    [[nodiscard]] bool activate();
    [[nodiscard]] bool complete();
    [[nodiscard]] bool abort();
    [[nodiscard]] bool reset();

    [[nodiscard]] InteractionState state() const noexcept { return state_; }
    [[nodiscard]] bool terminal() const noexcept;

private:
    InteractionState state_ = InteractionState::Idle;
};

} // namespace inkscape_android::input
