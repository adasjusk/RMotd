// commands for ops
fn rmotd_register(commands: &mut Vec<crate::game::native_server::NativeCommand>) {
    use crate::game::{native_server::NativeCommand, rmotd as m};
    m::start(crate::game::Game::native_config_dir().ok().as_deref());
    commands.push(NativeCommand::op("randommotd", 2));
    commands.push(NativeCommand::op("motd reload", 2));
    commands.push(NativeCommand::op("motd create <name:word> <motd:text>", 2));
}