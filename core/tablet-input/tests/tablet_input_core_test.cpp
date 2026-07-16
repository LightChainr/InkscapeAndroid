#include "inkscape_android/tablet_input_core.hpp"

#include <cstdlib>
#include <iostream>
#include <string_view>

using namespace inkscape_android::input;

namespace {

int failures = 0;

void expect(bool condition, std::string_view message)
{
    if (!condition) {
        std::cerr << "FAIL: " << message << '\n';
        ++failures;
    }
}

PointerEvent event(PointerPhase phase, DeviceKind device, int id)
{
    return PointerEvent{phase, device, id, 0.0, 0.0};
}

void test_pen_exclusivity()
{
    InputRouter router;
    auto down = router.handle(event(PointerPhase::Down, DeviceKind::Pen, 10));
    expect(down.owner == InputOwner::Pen, "pen down owns the canvas");
    expect(down.contains(RouterAction::ForwardPenDown), "pen down is forwarded");

    auto finger = router.handle(event(PointerPhase::Down, DeviceKind::Finger, 20));
    expect(finger.owner == InputOwner::Pen, "finger cannot steal pen ownership");
    expect(finger.contains(RouterAction::Consume), "finger is consumed during pen interaction");

    auto move = router.handle(event(PointerPhase::Move, DeviceKind::Pen, 10));
    expect(move.contains(RouterAction::ForwardPenMove), "active pen move is forwarded");

    auto up = router.handle(event(PointerPhase::Up, DeviceKind::Pen, 10));
    expect(up.contains(RouterAction::ForwardPenUp), "active pen up is forwarded");
    expect(up.owner == InputOwner::BlockUntilAllTouchUp, "remaining finger blocks ownership handoff");

    auto finger_up = router.handle(event(PointerPhase::Up, DeviceKind::Finger, 20));
    expect(finger_up.owner == InputOwner::None, "all touch released returns to idle");
}

void test_two_finger_navigation()
{
    InputRouter router;
    auto first = router.handle(event(PointerPhase::Down, DeviceKind::Finger, 1));
    expect(first.owner == InputOwner::TouchPending, "first finger only enters pending state");
    expect(first.contains(RouterAction::Consume), "first finger produces no canvas action");

    auto second = router.handle(event(PointerPhase::Down, DeviceKind::Finger, 2));
    expect(second.owner == InputOwner::TouchTransform, "second finger begins transform");
    expect(second.contains(RouterAction::BeginTouchTransform), "two fingers emit transform begin");

    auto move = router.handle(event(PointerPhase::Move, DeviceKind::Finger, 1));
    expect(move.contains(RouterAction::UpdateTouchTransform), "touch transform move is routed");

    auto one_up = router.handle(event(PointerPhase::Up, DeviceKind::Finger, 1));
    expect(one_up.contains(RouterAction::EndTouchTransform), "first release ends the whole transform");
    expect(one_up.owner == InputOwner::BlockUntilAllTouchUp, "remaining finger is blocked");

    auto remaining_move = router.handle(event(PointerPhase::Move, DeviceKind::Finger, 2));
    expect(remaining_move.contains(RouterAction::Consume), "remaining single finger cannot continue navigation");

    auto remaining_up = router.handle(event(PointerPhase::Up, DeviceKind::Finger, 2));
    expect(remaining_up.owner == InputOwner::None, "touch sequence fully releases ownership");
}

void test_pen_during_touch_is_ignored()
{
    InputRouter router;
    (void)router.handle(event(PointerPhase::Down, DeviceKind::Finger, 1));
    (void)router.handle(event(PointerPhase::Down, DeviceKind::Finger, 2));

    auto pen_down = router.handle(event(PointerPhase::Down, DeviceKind::Pen, 9));
    expect(pen_down.owner == InputOwner::TouchTransform, "pen cannot steal an active touch transform");
    expect(pen_down.contains(RouterAction::Consume), "pen sequence is ignored while touch owns canvas");

    auto pen_up = router.handle(event(PointerPhase::Up, DeviceKind::Pen, 9));
    expect(pen_up.contains(RouterAction::Consume), "ignored pen up is not forwarded");
}

void test_cancel_aborts_once()
{
    InputRouter router;
    InteractionSession session;
    expect(session.arm(), "session can arm from idle");
    expect(session.activate(), "session can become active");

    (void)router.handle(event(PointerPhase::Down, DeviceKind::Pen, 4));
    auto cancel = router.handle(event(PointerPhase::Cancel, DeviceKind::Pen, 4));
    expect(cancel.contains(RouterAction::AbortInteraction), "pen cancel emits abort");
    expect(session.abort(), "first abort transitions session");
    expect(session.state() == InteractionState::Aborted, "session is aborted");

    auto late_up = router.handle(event(PointerPhase::Up, DeviceKind::Pen, 4));
    expect(!late_up.contains(RouterAction::ForwardPenUp), "late up after cancel is not forwarded");
    expect(!session.complete(), "late release cannot commit an aborted session");
    expect(!session.abort(), "duplicate abort is ignored");
}

void test_lifecycle_cancel_all()
{
    InputRouter router;
    (void)router.handle(event(PointerPhase::Down, DeviceKind::Pen, 5));
    auto result = router.cancel_all();
    expect(result.contains(RouterAction::AbortInteraction), "lifecycle cancel aborts pen interaction");
    expect(result.owner == InputOwner::None, "lifecycle cancel clears ownership");
    expect(router.active_touch_count() == 0U, "lifecycle cancel clears touch registry");
    expect(!router.pen_interaction_active(), "lifecycle cancel clears pen registry");
}

void test_session_terminal_rules()
{
    InteractionSession session;
    expect(session.arm(), "arm succeeds");
    expect(session.complete(), "armed click can complete without drag activation");
    expect(session.terminal(), "committed session is terminal");
    expect(!session.abort(), "committed session cannot abort");
    expect(session.reset(), "terminal session can reset");
    expect(session.state() == InteractionState::Idle, "reset returns to idle");
    expect(!session.activate(), "idle session cannot activate without arm");
}

} // namespace

int main()
{
    test_pen_exclusivity();
    test_two_finger_navigation();
    test_pen_during_touch_is_ignored();
    test_cancel_aborts_once();
    test_lifecycle_cancel_all();
    test_session_terminal_rules();

    if (failures != 0) {
        std::cerr << failures << " test assertion(s) failed\n";
        return EXIT_FAILURE;
    }
    std::cout << "tablet input core tests: OK\n";
    return EXIT_SUCCESS;
}
