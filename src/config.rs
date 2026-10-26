// muguet - config.rs
// Constantin Clerc (c22dev) - 0.1

use std::collections::VecDeque;
use std::path::{Path, PathBuf};

#[derive(Clone)]
#[cfg_attr(target_os = "ios", allow(dead_code))]
pub struct Startup {
    pub wasm_path: PathBuf,
    pub cache_path: PathBuf,
    pub engine_args: Vec<String>,
}

const DEFAULT_CONFIG: &str = r#"{
  "scale": 0.5,
  "mouse_sensitivity": 2.0,
  "fullscreen": true,
  "fps_limit": 60,
  "start_mode": 2,
  "mute": false,
  "multiplayer": false,
  "host": false,
  "server": "",
  "player_name": "",
  "extra_args": [],
  "phone_lod": true,
  "phone_graphics": false
}
"#;

fn support_dir() -> PathBuf {
    #[cfg(target_os = "android")]
    return PathBuf::from(std::env::var_os("MUGUET_SUPPORT").expect("Android data directory not initialized"));
    #[cfg(not(target_os = "android"))]
    {
        let home = std::env::var("HOME").unwrap_or_else(|_| "/tmp".into());
        if cfg!(target_os = "ios") {
            return PathBuf::from(home).join("Documents");
        }
        PathBuf::from(home).join("Library/Application Support/Muguet")
    }
}

// The manifest has to be readable, not merely present: an app can stat a file on
// /sdcard that it is not allowed to open, which made a permission problem look
// like missing game data.
fn readable(dir: &Path) -> bool {
    std::fs::File::open(dir.join("data/manifest.json")).is_ok()
}

/// Reports exactly what is missing from a folder the user picked, so the launcher
/// can say "this is not a game folder" instead of failing later inside the engine.
pub fn validate_game_root(start: &Path) -> Result<PathBuf, String> {
    let root = find_game_root(start).ok_or_else(|| {
        format!(
            "{} is not a game folder: it must contain data/manifest.json and b/<build>/shaders/index.json",
            start.display()
        )
    })?;
    if !root.join("data/manifest.json").is_file() {
        return Err(format!("{} has no data/manifest.json", root.display()));
    }
    let Some(build) = build_dir(&root) else {
        return Err(format!(
            "{} has no usable b/<build>/shaders/index.json",
            root.display()
        ));
    };
    #[cfg(feature = "jit")]
    if !crate::code_embedded() && !build.join("game.wasm").is_file() {
        return Err(format!("{} has no b/<build>/game.wasm", build.display()));
    }
    #[cfg(not(feature = "jit"))]
    let _ = build;
    Ok(root)
}

// The release ships the game inside a wrapper folder: GrandV-assets/mirror/
// playgta5.com holds data/ and b/<build>/, and the instructions tell people to
// pick the outer folder. The game root is the folder that holds both, so the
// app looks for it inside whatever was picked instead of demanding one exact
// path. The search is shallow and bounded - it never walks the game tree.
const ROOT_SEARCH_DEPTH: usize = 4;
const ROOT_SEARCH_DIRS: usize = 512;
const CONTENT_DIRS: &[&str] = &[
    "data", "b", "shaders", "x64", "common", "title", "vendor", "home", "proc", "tmp", "dev",
    "userdata", "_mods", "_movie", "_map_support", "branding", "touch_art",
];

fn is_game_root(dir: &Path) -> bool {
    readable(dir) && build_dir(dir).is_some()
}

pub fn find_game_root(start: &Path) -> Option<PathBuf> {
    let mut queue: VecDeque<(PathBuf, usize)> = VecDeque::new();
    queue.push_back((start.to_path_buf(), 0));
    let mut visited = 0usize;
    while let Some((dir, depth)) = queue.pop_front() {
        visited += 1;
        if visited > ROOT_SEARCH_DIRS {
            break;
        }
        if is_game_root(&dir) {
            return Some(dir);
        }
        if depth >= ROOT_SEARCH_DEPTH {
            continue;
        }
        let Ok(kids) = std::fs::read_dir(&dir) else { continue };
        for kid in kids.flatten() {
            if !kid.file_type().map(|t| t.is_dir()).unwrap_or(false) {
                continue;
            }
            let name = kid.file_name();
            let name = name.to_string_lossy();
            if name.starts_with('.') || CONTENT_DIRS.contains(&name.as_ref()) {
                continue;
            }
            queue.push_back((kid.path(), depth + 1));
        }
    }
    None
}

pub fn game_dir() -> PathBuf {
    // A folder the user picked in the system file browser stands in for the
    // imported copy. The engine only ever reads the game directory (saves live in
    // MUGUET_FSROOT), so nothing has to be copied onto the device -- which is
    // what makes this usable on a phone with no free space left.
    if let Some(picked) = picked_folder() {
        if let Some(root) = find_game_root(&picked) {
            return root;
        }
    }
    support_dir().join("game")
}

// The launcher records the picked folder in config.json because the game runs in
// its own process (android:process=":game"), where an environment variable set
// through JNI in the launcher does not exist.
fn picked_folder() -> Option<PathBuf> {
    std::env::var_os("MUGUET_GAME_DIR")
        .filter(|p| !p.is_empty())
        .map(PathBuf::from)
        .or_else(|| {
            let text = std::fs::read_to_string(support_dir().join("config.json")).ok()?;
            let path = serde_json::from_str::<serde_json::Value>(&text).ok()?.get("game_folder_path")?.as_str()?.trim().to_string();
            (!path.is_empty()).then(|| PathBuf::from(path))
        })
}

pub fn game_ready() -> bool {
    let dir = game_dir();
    dir.join("data/manifest.json").exists() && build_dir(&dir).is_some_and(|b| !cfg!(feature = "jit") || crate::code_embedded() || b.join("game.wasm").exists())
}

pub fn bundled_resources() -> Option<PathBuf> {
    let exe = std::env::current_exe().ok()?;
    let res = exe.parent()?.parent()?.join("Resources");
    (exe.parent()?.ends_with("Contents/MacOS") && res.is_dir()).then_some(res)
}

fn build_dir(root: &Path) -> Option<PathBuf> {
    std::fs::read_dir(root.join("b")).ok()?.flatten().map(|e| e.path()).find(|p| p.join("shaders/index.json").exists())
}

fn set_default(key: &str, value: impl AsRef<std::ffi::OsStr>) {
    if std::env::var_os(key).is_none() {
        std::env::set_var(key, value);
    }
}
