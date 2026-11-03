// End of each server tick: the next MOTD on the LAN host, when it is time.
fn rmotd_tick(
    game: &mut crate::game::Game<'_>,
    phase: crate::game::native_lifecycle::ServerTickPhase,
) {
    use crate::game::native_lifecycle::ServerTickPhase;
    if !matches!(phase, ServerTickPhase::ServerTickEnd { .. }) {
        return;
    }
    if let Some(motd) = crate::game::rmotd::tick()
        && let Some(host) = &game.lan
    {
        horizon_client::lan::rmotd_motd::set(&host.hub.lan.shared, motd);
    }
}
