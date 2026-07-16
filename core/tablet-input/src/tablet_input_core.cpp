#include "inkscape_android/tablet_input_core.hpp"

#include <algorithm>
#include <utility>

namespace inkscape_android::input {

bool RouterResult::contains(RouterAction action) const
{
    return std::find(actions.begin(), actions.end(), action) != actions.end();
}

RouterResult InputRouter::result(std::vector<RouterAction> actions) const
{
    return RouterResult{owner_, std::move(actions)};
}

RouterResult InputRouter::handle(PointerEvent const &event)
{
    switch (event.device) {
        case DeviceKind::Pen:
        case DeviceKind::Eraser:
            return handle_pen(event);
        case DeviceKind::Finger:
            return handle_touch(event);
        case DeviceKind::Mouse:
        case DeviceKind::Unknown:
            return result({RouterAction::Consume});
    }
    return result({RouterAction::Consume});
}

RouterResult InputRouter::handle_pen(PointerEvent const &event)
{
    switch (event.phase) {
        case PointerPhase::Down:
            if (owner_ == InputOwner::None) {
                owner_ = InputOwner::Pen;
                pen_id_ = event.pointer_id;
                return result({RouterAction::ForwardPenDown});
            }
            ignored_pen_ids_.insert(event.pointer_id);
            return result({RouterAction::Consume});

        case PointerPhase::Move:
            if (owner_ == InputOwner::Pen && pen_id_ == event.pointer_id) {
                return result({RouterAction::ForwardPenMove});
            }
            return result({RouterAction::Consume});

        case PointerPhase::Up:
            if (ignored_pen_ids_.erase(event.pointer_id) != 0U) {
                return result({RouterAction::Consume});
            }
            if (owner_ == InputOwner::Pen && pen_id_ == event.pointer_id) {
                pen_id_.reset();
                owner_ = touch_ids_.empty() ? InputOwner::None : InputOwner::BlockUntilAllTouchUp;
                return result({RouterAction::ForwardPenUp});
            }
            return result({RouterAction::Consume});

        case PointerPhase::Cancel:
            ignored_pen_ids_.erase(event.pointer_id);
            if (owner_ == InputOwner::Pen && pen_id_ == event.pointer_id) {
                pen_id_.reset();
                owner_ = touch_ids_.empty() ? InputOwner::None : InputOwner::BlockUntilAllTouchUp;
                return result({RouterAction::AbortInteraction});
            }
            return result({RouterAction::Consume});
    }
    return result({RouterAction::Consume});
}

RouterResult InputRouter::handle_touch(PointerEvent const &event)
{
    switch (event.phase) {
        case PointerPhase::Down: {
            touch_ids_.insert(event.pointer_id);
            if (owner_ == InputOwner::None) {
                owner_ = InputOwner::TouchPending;
                return result({RouterAction::Consume});
            }
            if (owner_ == InputOwner::TouchPending && touch_ids_.size() == 2U) {
                owner_ = InputOwner::TouchTransform;
                return result({RouterAction::BeginTouchTransform});
            }
            return result({RouterAction::Consume});
        }

        case PointerPhase::Move:
            if (owner_ == InputOwner::TouchTransform && touch_ids_.contains(event.pointer_id)) {
                return result({RouterAction::UpdateTouchTransform});
            }
            return result({RouterAction::Consume});

        case PointerPhase::Up: {
            touch_ids_.erase(event.pointer_id);
            if (owner_ == InputOwner::TouchPending) {
                if (touch_ids_.empty()) {
                    owner_ = InputOwner::None;
                }
                return result({RouterAction::Consume});
            }
            if (owner_ == InputOwner::TouchTransform) {
                owner_ = touch_ids_.empty() ? InputOwner::None : InputOwner::BlockUntilAllTouchUp;
                return result({RouterAction::EndTouchTransform});
            }
            if (owner_ == InputOwner::BlockUntilAllTouchUp && touch_ids_.empty()) {
                owner_ = InputOwner::None;
            }
            return result({RouterAction::Consume});
        }

        case PointerPhase::Cancel: {
            touch_ids_.erase(event.pointer_id);
            if (owner_ == InputOwner::TouchTransform) {
                owner_ = touch_ids_.empty() ? InputOwner::None : InputOwner::BlockUntilAllTouchUp;
                return result({RouterAction::EndTouchTransform});
            }
            if ((owner_ == InputOwner::TouchPending || owner_ == InputOwner::BlockUntilAllTouchUp) && touch_ids_.empty()) {
                owner_ = InputOwner::None;
            }
            return result({RouterAction::Consume});
        }
    }
    return result({RouterAction::Consume});
}

RouterResult InputRouter::cancel_all()
{
    std::vector<RouterAction> actions;
    if (owner_ == InputOwner::Pen && pen_id_.has_value()) {
        actions.push_back(RouterAction::AbortInteraction);
    }
    if (owner_ == InputOwner::TouchTransform) {
        actions.push_back(RouterAction::EndTouchTransform);
    }
    if (actions.empty()) {
        actions.push_back(RouterAction::Consume);
    }

    owner_ = InputOwner::None;
    pen_id_.reset();
    ignored_pen_ids_.clear();
    touch_ids_.clear();
    return result(std::move(actions));
}

bool InteractionSession::arm()
{
    if (state_ != InteractionState::Idle) {
        return false;
    }
    state_ = InteractionState::Armed;
    return true;
}

bool InteractionSession::activate()
{
    if (state_ != InteractionState::Armed) {
        return false;
    }
    state_ = InteractionState::Active;
    return true;
}

bool InteractionSession::complete()
{
    if (state_ != InteractionState::Armed && state_ != InteractionState::Active) {
        return false;
    }
    state_ = InteractionState::Committed;
    return true;
}

bool InteractionSession::abort()
{
    if (state_ != InteractionState::Armed && state_ != InteractionState::Active) {
        return false;
    }
    state_ = InteractionState::Aborted;
    return true;
}

bool InteractionSession::reset()
{
    if (state_ == InteractionState::Armed || state_ == InteractionState::Active) {
        return false;
    }
    state_ = InteractionState::Idle;
    return true;
}

bool InteractionSession::terminal() const noexcept
{
    return state_ == InteractionState::Committed || state_ == InteractionState::Aborted;
}

} // namespace inkscape_android::input
