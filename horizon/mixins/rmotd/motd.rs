// The MOTD the server list shows from now on (MinecraftServer.setMotd).
pub fn set(shared: &super::Shared, motd: String) {
    *shared.motd.lock().unwrap_or_else(|e| e.into_inner()) = motd;
}
