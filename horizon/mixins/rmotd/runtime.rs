// RMotd for Horizon, the module crate::game::rmotd and the Cargo library for cargo test.
use serde_json::{Value, json};
use std::cell::RefCell;
use std::path::{Path, PathBuf};

/// Ticks between two MOTDs, as the Fabric build.
pub const ROTATION_TICKS: u32 = 200;

struct State {
    path: Option<PathBuf>,
    config: Value,
    index: usize,
    ticks: u32,
}

thread_local! {
    static STATE: RefCell<State> = RefCell::new(State {
        path: None,
        config: defaults(),
        index: 0,
        ticks: 0,
    });
}

/// The Fabric build's config/rmotd.json.
fn defaults() -> Value {
    json!({
        "randomMotdEnabled": true,
        "motds": [
            "&c&lWelcome &b&lto our server!",
            "&eExplore the world of Minecraft with us!",
            "&aJoin us and have fun!"
        ],
        "messages": {
            "invalidInput": "Invalid input",
            "settingUpdated": "Random MOTD feature updated to <status>",
            "enabled": "enabled",
            "disabled": "disabled"
        }
    })
}

/// Reads the file, writing the defaults when it is missing.
fn read(path: &Path) -> Value {
    match std::fs::read_to_string(path) {
        Ok(text) => serde_json::from_str(&text).unwrap_or_else(|_| defaults()),
        Err(_) => {
            let config = defaults();
            write(path, &config);
            config
        }
    }
}

fn write(path: &Path, config: &Value) {
    let text = serde_json::to_string_pretty(config).unwrap_or_default();
    let _ = path
        .parent()
        .map_or(Ok(()), std::fs::create_dir_all)
        .and_then(|()| std::fs::write(path, text));
}

fn message(config: &Value, key: &str) -> String {
    config["messages"][key]
        .as_str()
        .map(str::to_owned)
        .unwrap_or_else(|| defaults()["messages"][key].as_str().unwrap_or_default().to_owned())
}

/// A world opens: read config/ (`dir`) and show the first MOTD at once.
pub fn start(dir: Option<&Path>) {
    let path = dir.map(|d| d.join("rmotd.json"));
    let config = path.as_deref().map_or_else(defaults, read);
    STATE.with(|s| *s.borrow_mut() = State { path, config, index: 0, ticks: ROTATION_TICKS - 1 });
}

/// /motd reload.
pub fn reload() -> String {
    STATE.with(|s| {
        let mut s = s.borrow_mut();
        if let Some(path) = s.path.clone() {
            s.config = read(&path);
        }
    });
    "Config reloaded.".into()
}

/// /randommotd: flips rotation and keeps it.
pub fn toggle() -> String {
    STATE.with(|s| {
        let mut s = s.borrow_mut();
        let on = !s.config["randomMotdEnabled"].as_bool().unwrap_or(true);
        s.config["randomMotdEnabled"] = on.into();
        if let Some(path) = &s.path {
            write(path, &s.config);
        }
        if on {
            s.ticks = ROTATION_TICKS - 1;
        }
        let status = message(&s.config, if on { "enabled" } else { "disabled" });
        message(&s.config, "settingUpdated").replace("<status>", &status)
    })
}

/// /motd create <name> <motd>: adds it to the list and keeps it.
pub fn create(name: &str, motd: &str) -> String {
    STATE.with(|s| {
        let mut s = s.borrow_mut();
        if !s.config["motds"].is_array() {
            s.config["motds"] = json!([]);
        }
        if let Some(list) = s.config["motds"].as_array_mut() {
            list.push(motd.into());
        }
        if let Some(path) = &s.path {
            write(path, &s.config);
        }
    });
    format!("Created MOTD '{name}' and saved to config.")
}

/// The next MOTD every ROTATION_TICKS while rotation is on.
pub fn tick() -> Option<String> {
    STATE.with(|s| {
        let mut s = s.borrow_mut();
        if !s.config["randomMotdEnabled"].as_bool().unwrap_or(true) {
            return None;
        }
        s.ticks += 1;
        if s.ticks < ROTATION_TICKS {
            return None;
        }
        s.ticks = 0;
        let motds: Vec<&str> = s.config["motds"].as_array()?.iter().filter_map(Value::as_str).collect();
        let motd = motds.get(s.index % motds.len().max(1)).map(|m| colors(m))?;
        s.index = (s.index + 1) % motds.len();
        (!motd.is_empty()).then_some(motd)
    })
}

/// `&c` to `§c`, for the codes Minecraft knows.
pub fn colors(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    let mut chars = text.chars().peekable();
    while let Some(c) = chars.next() {
        match chars.peek() {
            Some(&n) if c == '&' && "0123456789abcdefklmnor".contains(n.to_ascii_lowercase()) => {
                out.push('§');
                out.push(n.to_ascii_lowercase());
                chars.next();
            }
            _ => out.push(c),
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn translates_color_codes() {
        assert_eq!(colors("&c&LHi & bye&"), "§c§lHi & bye&");
    }

    #[test]
    fn rotates_and_toggles() {
        let dir = std::env::temp_dir().join(format!("rmotd-test-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        start(Some(&dir));
        assert!(dir.join("rmotd.json").is_file(), "defaults written");
        assert_eq!(tick().as_deref(), Some("§c§lWelcome §b§lto our server!"));
        assert_eq!((1..ROTATION_TICKS).filter_map(|_| tick()).count(), 0);
        assert_eq!(tick().as_deref(), Some("§eExplore the world of Minecraft with us!"));
        assert_eq!(toggle(), "Random MOTD feature updated to disabled");
        assert!((0..ROTATION_TICKS * 2).all(|_| tick().is_none()));
        create("x", "&aNew");
        // Kept on disk: a reload still has it and stays off.
        reload();
        assert_eq!(toggle(), "Random MOTD feature updated to enabled");
        let shown: Vec<String> = (0..ROTATION_TICKS * 4).filter_map(|_| tick()).collect();
        assert!(shown.contains(&"§aNew".to_owned()), "{shown:?}");
        let _ = std::fs::remove_dir_all(&dir);
    }
}
