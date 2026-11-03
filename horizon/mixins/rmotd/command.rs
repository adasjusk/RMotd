// /randommotd, /motd reload and /motd create <name> <motd>.
fn rmotd_command(game: &mut crate::game::Game<'_>, command: &str, ran: &mut bool) {
    use crate::game::rmotd as m;
    use horizon_client::text::Text;
    if *ran {
        return;
    }
    let mut words = command.splitn(4, ' ');
    let answer = match (words.next(), words.next(), words.next(), words.next()) {
        (Some("randommotd"), None, ..) => m::toggle(),
        (Some("motd"), Some("reload"), None, _) => m::reload(),
        (Some("motd"), Some("create"), Some(name), Some(motd)) if !motd.trim().is_empty() => m::create(name, motd),
        _ => return,
    };
    *ran = true;
    game.native_feedback(Text::literal(answer));
}
