# CxVisuals

Client-side Fabric mod for Minecraft 26.2 and 26.3: a set of visual modules behind one settings window.

It only changes what you see. Your player's position and rotation are never touched. The social modules (friends, shared maps, presence, prices) use the CxVisuals cloud and are described under Privacy; everything else stays on your computer.

## Modules

### Idle Camera

When you go AFK the camera leaves first person and plays a third-person showcase of your player, in the style of the idle camera in Forza Horizon: slow orbits, low hero angles, push-ins, crane moves, close-ups, with eased transitions and a little roll. Any input hands the camera back.

It starts on its own after 60 seconds without input (configurable) and ends on any key, mouse movement, click, scroll, or when you take damage. By default the camera stays out of blocks and goes around walls instead of through them.

With Play in pause menu on, it also starts while the pause menu is open. The menu stays open (a singleplayer game stays paused) but is hidden together with the cursor while the cinematic plays. The input that brings the menu back only wakes it; it is not passed on, so it cannot press a button you could not see.

### Hands

Changes how your first-person hands and held items look. Each hand has its own position, scale and rotation, so an item can sit lower, further out, bigger, or lie sideways. The swing can be replaced by one of five animations (Spin, Flip, Slash, Stab, Tap) with adjustable intensity and speed, the held item can spin while idle, and the dip when switching items can be turned off. Off by default.

### Motion Blur

Blurs the world along the way it really moves across the screen when you turn or travel, like a camera shutter. It works per pixel from the depth buffer instead of blending old frames, so the hand, the HUD and menus stay sharp, nothing leaves ghost trails, and the look is the same at any frame rate. Near things streak more than far things when you move.

Turning and Moving have separate strength, so you can keep mouse aim crisp and still get speed streaks while sprinting, riding or gliding. Sharp centre keeps the area around the crosshair clear, and Quality sets the sample count. Off by default.

Shader packs (Iris) draw the hand into the world, so there it would blur with everything else. Hand blur sets how much of the blur the hand may get; at 0, the default, it stays sharp.

### Animations

Small motion on things that normally snap. Purely visual: nothing is sent to the server and your real position never changes. Off by default. Speed scales every animation.

- **Hotbar.** The selection frame glides to the slot you switch to with a slight overshoot (Selector: Glide or Snap). The item you switch to gives a small bounce, and a slot bounces when its stack grows, for example on pickup.
- **Teleport.** When the server moves you, the camera flies from where you were to where you are now, turning as it goes. Duration sets how long. Moves shorter than Minimum distance stay instant, so rubber-banding is not smoothed. Moves longer than Maximum distance, and dimension changes and respawns, fade in from black instead. Style: Glide, Fade or Off. The flight takes the straight line, so on a medium jump it can pass through blocks for a moment. It is skipped in spectator, in third person and while the Idle Camera is running.
- **Inventory.** When a stack moves between slots (shift-click, number keys, swaps) it travels there instead of jumping. Sliding items turns this on or off. Soft cursor item makes the stack you are holding trail the cursor slightly. Opening a container or a full resync never animates.
- **Screens.** Inventory, chests and menus pop or slide in when they open (Opening: Pop, Slide or Off). The dimmed background stays still. There is no closing animation.
- **Status.** Smooth bars eases health, hunger, armor and the XP bar to their new value. Damage ghost keeps lost hearts visible for a moment. Number pop pulses the XP level when it changes.
- **Text.** Chat slide lets a new line push the others up smoothly. Tooltip pop and List pop scale tooltips, the player list and the scoreboard into place.

Vanilla draws items and text without a fade control, so the pops are scale based rather than true fades.

### Spatial UI

Gives the interface depth. The settings window turns toward your cursor in real perspective. Its cards, rows and controls float at different heights above the panel and slide against each other, shadows fall away from the light, and a soft sheen moves across the glass. The dimmed background stays put. Hovered controls lift toward you. In game, HUD elements float at their own depth and sway with your look and strafing: the hotbar sits nearest, info cards in the middle and the chat feed furthest away.

On by default. Strength sets how far everything moves. Game screens (off by default) turns the inventory, chests and menus the same way: the whole screen tilts as one card while the dimmed or blurred background behind it stays still, and clicks land where things are drawn. Chat is left flat. Camera sway, Focus pop and Sheen can each be turned off. While you hold a slider or other control the tilt holds still, so it does not slide away under the cursor. HUD → Layout → Reduce motion freezes the HUD sway, and the layout editor always shows elements at their saved spots.

It lives in Settings under its own Spatial UI tab.

### HUD

Info elements in the style of the settings window, and a layout editor that also moves and resizes the vanilla HUD. Off by default.

Info elements: FPS, Ping, CPU, GPU, RAM, TPS, Speed, CPS, Position, Clock, Session, Keystrokes, Armor, Held items, Nearby players, Server info, Network, Target, Chat feed and Window.

Server info, Network and Target have a Detail option. Visible (the default) shows only what you could see in the game itself: server name, player count, world and connection bars; a status word and the bar history; the name, rough distance and worn gear of what is under your crosshair. Full adds the exact numbers: address, ping, TPS and server software; milliseconds, jitter and packets per second; health, armor points, exact distance and item durability. Target keeps showing the last thing you aimed at for a moment after you look away.

Chat feed is a compact, self-fading list of the newest chat and system messages. Lines sets how many rows show, Width where long messages wrap, Visible for how long a message stays. It follows your chat visibility setting and is cleared when you leave a world.

Nearby players lists the players you can currently see, nearest first, with their head and distance. Rows slide in when someone comes into view, fade out when they leave, and glide to their new place as the order changes; the ring around a head turns red for anyone within 8 blocks. Invisible players and spectators are never listed. Only in sight (on by default) limits it to players inside your view with a clear line of sight; off lists every loaded player. Server names (on by default) draws each name the way the server formats it, with rank, colours and effects such as obfuscated text, in the Minecraft font; off shows the plain name in the interface font. The numeric ones come as a pill, a tile with a segment meter, or a graph with a sparkline, and turn their dot red when the reading is bad (low FPS, high ping, high load).

Window shows a live copy of another application on your computer, for example a Discord stream or a video call. Pick the window under HUD → Layout → Window mirror and switch the Window element on. Windows uses Windows Graphics Capture, so covered windows and windows on other monitors keep updating with about a frame of delay. On Linux with X11 the window list works the same way, and on Wayland the desktop's share dialog picks the window once and remembers it. A minimized window cannot be drawn by the system, so it freezes on its last frame. Quality trades detail for video memory bandwidth. The mirror is view only.

Game elements: Scoreboard, Boss bars, Effects, Action bar, Titles, Hotbar, Item name, Chat and Player list. Each can be moved, resized or hidden. Titles have a fixed box: a title wider than the box is shrunk to fit it, so resize the box to choose how big titles may get. Boss bars sit on one card with their names; turn Background off to keep the bars alone. Scoreboard, boss bars, effect tiles and the player list are redrawn on interface-style cards and bars; their text stays in the vanilla font so server colours and resource-pack glyphs survive. Turn Themed off per element to keep the vanilla look.

Edit layout (the button in the HUD header, or its key) opens the editor over the running game:

- drag an element to move it; red lines show when it lines up with an edge, the centre or another element
- scroll over it or pull its corner knob to resize
- right-click it for its options
- Elements opens the tray: click a chip to show or hide an element, or drag it out to place it. The tray sits above the top bar and takes its clicks, so Help and Done behind it cannot be pressed by accident
- while you drag, small labels show the distance to the nearest neighbour or screen edge on each side
- Help replays the three-step tour that runs the first time the editor opens
- drag across empty space, or Shift-click, to select several; they then move as one, and a plain click picks a single one again
- arrow keys nudge the selection (Shift for bigger steps), Delete hides it

Elements keep a gap between each other (Spacing, under HUD → Layout): a dragged element stops and slides along its neighbours instead of overlapping them, and will not grow into one. This covers info elements, scoreboard, boss bars, effects and hotbar; chat, titles, action bar and player list are left out because their areas span most of the screen.

Make room (on by default) keeps that gap while you play too. When an element that changes size, such as Nearby players or Armor, grows into a neighbour, the neighbour glides the shortest way out and glides back once the space is free. Saved positions are not changed, and the editor always shows elements at their saved spots.

Numbers roll: when a reading changes, only the digits that changed slide out and in. Reduce motion (HUD → Layout) turns off rolling, fades, sliding rows and make-room glides, in the HUD and in the editor.

Adaptive text reads what is behind each pixel of HUD text and draws it dark over bright surroundings and light over dark ones, so labels stay readable over sky, snow or caves. Accent colours such as the red facing letter keep their colour.

Positions are stored relative to the nearest screen edge or centre, so a layout survives a change of resolution. Background picks the card style (Window, Solid, Clear, Glass) and Size scales all info elements together.

### World Map
A minimap and a full map with relief shading, a cave view, clouds and a 3D mode with overhangs.

- Loads in the background: a few seconds after you join a world the map builds its atlas and bakes the tiles around you, so opening it does not stall. Tiles that are still baking show a soft pulsing dot pattern until they land
- Block textures fade in as you zoom close, up to 40 px per block
- 3D mode: the terrain is a clean slab. Cave floors under the edge are hidden, and tall side walls and the underground fade into the cloud sea below the map
- Search for blocks, place waypoints, see player heads and cards
- Zoom out without a limit, with chunk previews
- Shared live maps: friends fill one end-to-end encrypted map together, with an optional backfill of what you already explored
- Live location per friend, off by default, for a duration you choose
- Explore: publish a map to a public page or browse maps others published
- A button on DonutSMP shows the server regions

### Seed forecast
Off by default. Switch on Predict unexplored areas, give the map a world seed, and it draws the places you have not explored yet from the seed, in all three dimensions. Explored areas stay crisp and replace the prediction as you explore, so player builds and changes always show once you have seen them.

- Set it under Map → Forecast → World seed, or just run `/seed` in chat: the map picks up the reply. The seed is saved for each world and server on your PC only, and nothing is predicted until Predict unexplored areas is on.
- Numbers and words both work, as when creating a world.
- The prediction uses the game's own terrain generation, so it matches vanilla servers of the same Minecraft version. It shows biomes and terrain height, not trees, structures, caves or builds, and it does not match servers with custom world generation.
- It is drawn muted, with relief shading and the drifting mist, and the title card says Forecast from seed while it is on. Predict unexplored areas turns it on and off.
- It runs on background threads in the same tile pipeline as the explored map, about 3 ms per tile up close and 15 ms zoomed far out, and adds no frame hitches in the stress test.

### Waypoints
Named spots per world and dimension, shown in the world, on the minimap and on the full map. Add one from the Waypoints tab or with its key.

- **Icons.** Every waypoint can carry one of 12 pictograms: home, base, mine, farm, village, portal, chest, sword, tree, boat, star and skull. The icon replaces the colour dot on the in-world label, the map, the minimap and the places list. Click a waypoint's icon in the Waypoints tab to change it, or pick the plain dot to go back. Deaths use the skull.
- **Suggestions from the name.** The icon is chosen from words in the name ("Diamond mine" gets the pickaxe), in English, German and Russian. In the icon picker the suggested icon is listed first.
- **Suggestions from where you stand.** Adding a waypoint without a name looks around you and names it for you: Portal near a nether or end portal, Dungeon near a spawner, Village with two or more villagers close by, Farm over farmland, Home near beds, crafting tables or furnaces, Storage near chests or barrels, Ocean in water, Mine underground without sky. Repeats are numbered. When nothing fits it is called Waypoint with a number.
- **In-world label.** The label is a compact pill with the name. When you look at it, it eases open to show the distance and fades back as you look away.

### Main menu
The custom main menu shows the title screen's buttons as channels next to your worlds, servers and latest screenshot. Buttons added by other mods appear there too, so Mod Menu gets its own Mods channel without any setup, and other mods' buttons (including icon-only ones) become channels as well. Mod buttons use a mods icon; vanilla Language and Accessibility stay where they are. Channels can be rearranged and widgets placed in empty slots.

### World Download
Records the chunks you load and the containers you open into `saves/<name>`, so the world opens in singleplayer. Only the Overworld is written as void; the Nether and End use vanilla generators. Maps you explored earlier cannot become a world because they store no blocks.

### Texture Mixer
Pick single textures from any pack, per item and block, and compare every version side by side. Changes apply while you play through a generated `CxVisuals Mix` pack, and Modrinth is searchable from inside the game.

### Avatar
Import a glTF, GLB, OBJ, Blockbench, PLY, STL or VOX model and it replaces your player model for everyone who has the mod. Assign your own idle, walk, run and jump animations.

### Accounts
Switch between Microsoft, offline and token accounts without restarting. The list is kept in a local vault protected by your password and is never uploaded. The mod can rejoin the server you were on.

### World effects
- **Sky:** galaxies, planets, auroras, sunsets and storms
- **Atmosphere:** fireflies, petals, snow, butterflies, lanterns, meteors, fireworks
- **Entity glow:** plasma, aura, frost, outline, hologram, glitch and signal on players, mobs and your hand
- **Jump circles** and **entity trails**
- **Death effects:** ritual, beam, dissolve, glitch, matrix, void
- **Block outline:** animated edges, corners or scan
- **Fullbright:** luminance, gamma or night vision with an optional tint
- **Swing trails:** smooth ribbons behind weapons, in the world and in first person

### Social and utility
- Friends and chat with end-to-end sealed messages and files
- Presence: nearby players see your avatar and the song you play
- Auction house prices merged into one median per item (shown on the website)
- Item list with recipes, container previews, chat filter, zoom, nametags, live lyrics

## Privacy

Nothing leaves your computer unless you use a cloud feature. The modules that talk to the CxVisuals cloud are Friends, Presence, Shared maps, Explore and Prices.

| Data | Where it goes |
|---|---|
| Minecraft UUID and name, block list, the one avatar you share, friends and requests, your public key | Stored on the cloud |
| Friend messages and files | Sealed on your device, the server cannot read them, deleted after 30 days |
| Shared map rooms | Sealed with a key only members hold, kept up to 60 days after the last activity and deleted when the owner rotates the key |
| Location | Off until you enable it, per friend, sealed for one friend, held in server memory for 10 seconds and never written to disk |
| Explore maps | Public once you publish: title, server, dimension, size, preview and your name (or Anonymous). Remove them any time |
| Auction prices | Only from donutsmp.net, filed under a one-way code, deleted after 48 hours |
| Listening and status | Memory only, forgotten shortly after you stop |
| Accounts you add | Local vault only, never uploaded |

Your Minecraft access token, password, audio, in-game chat and your position are never sent without you choosing a friend and a duration. Account, then Delete cloud data, erases your account, friends, requests, messages and files immediately.

The mod is not affiliated with Mojang, Microsoft or DonutSMP.

## Use

| Key | Action |
|---|---|
| Right Shift | Open settings |
| F8 | Start or stop the idle cinematic now |

Every module also has a toggle key, unbound by default. All keys can be rebound in the settings window or in vanilla Controls.

The window lists modules on the left; each has its own switch, actions and tabs. Mod-wide options (appearance, open key, reset) are under Settings at the bottom of the list.

The window reopens where you left it: same module, same tab, same scroll position. Right-click a choice with many options (such as the swing animation) to unfold all of them as a grid; right-click again to fold it away.

Under Settings you can also pick how the window opens and closes (Morph, Pop, Slide or Fade).

Under Settings the window itself can be switched between three styles: Solid, Clear (see-through by the Opacity amount) and Glass (frosted, refracting liquid glass over the world, tinted by Opacity).

Under Settings → Configs you can save every setting of every module, HUD layout included, under a name: type a name and press Save, then Load to switch, Update to overwrite it with the current settings, Delete to remove it. The dot marks the config loaded last. Configs are files in `config/cxvisuals/configs/`, so they can be copied between installs. Key bindings are not part of a config.

Settings are stored in `config/cxvisuals.json`. A `caridle.json` from the earlier version is imported once.

## Build

Requires JDK 25.

```
./gradlew assemble
```

The jar for the newest version is written to `build/libs/`. Pick another with `-Pmc=26.2`; `./buildall.sh` builds every supported version.

## Supported versions

| Minecraft | Fabric Loader | Fabric API | Sodium |
|---|---|---|---|
| 26.3 | 0.20.1 | 0.165.0+26.3 | mc26.3-0.9.2 |
| 26.2 | 0.19.3 | 0.154.2+26.2 | mc26.2-0.9.2 |

`versions/<mc>.properties` holds the pins of each version. `src/main` is written against the newest version. Older versions are produced at build time from `versions/<mc>/`: `imports.txt` renames moved classes, `java.rules` and `shader.rules` are regex rewrites, and `src/mc/<mc>/` replaces the files that cannot be expressed as rules. Differences that cross many files live in `me.cxiy.cxvisuals.mc.compat`. To add a version, copy a `versions/*.properties`, build, and fix what fails.

```
./gradlew runClient
```

starts a dev client.

## Releases

Every push to `main` runs the Release workflow. It builds every supported version and publishes the GitHub release for the current `mod_version` (for example `v0.1.1`), replacing the previous build of that version. The release holds one jar per Minecraft version, `SHA256SUMS.txt`, one `manifest-<minecraft>.json` per jar and notes taken from the commits since the previous tag. Pushing a tag that matches `mod_version` or starting the workflow by hand builds the same release.

Nothing is uploaded to the servers by hand. The CxVisuals cloud checks the newest release every five minutes, downloads any jar whose manifest changed, verifies its size, SHA-1 and SHA-256 against the manifest and only then serves it, so installs update from the release without a separate deploy. `systemctl kill -s USR1 cxcloud` on the server makes it check right away.

The Build workflow compiles both versions on every push and pull request.

## Layout

| Path | Contents | May import Minecraft |
|---|---|---|
| `src/lib/java/me/cxiy/cxvisuals/core` | module base, registry, settings, config, maths | no |
| `src/lib/java/me/cxiy/cxvisuals/modules/<name>` | a module's logic and settings | no |
| `src/lib/java/me/cxiy/cxvisuals/gui` | widgets, window, text engine | no |
| `src/main/java/me/cxiy/cxvisuals/mc` | init, key mappings, canvas, screen | yes |
| `src/main/java/me/cxiy/cxvisuals/mc/modules/<name>` | a module's Minecraft hooks | yes |
| `src/main/java/me/cxiy/cxvisuals/mc/mixin` | all mixins | yes |

`src/lib` is compiled without Minecraft on the classpath (only Gson and JOML), so the rule is enforced by the build. Porting to another Minecraft version means touching `src/main` only.

## Adding a module

1. In `modules/<name>`, write a class extending `Module`: give it an id and name, declare sections, groups and settings, and any header actions.
2. In `mc/modules/<name>`, write its Minecraft hooks and read the module's settings from there.
3. Register it in `CxVisualsClient` with `registry.add(...)`.
4. Add its key names (`key.cxvisuals.<id>.toggle` and any others it binds) to `assets/cxvisuals/lang/en_us.json`.

The window entry, master switch, tabs, config keys (`<id>.<section>.<setting>`) and toggle key come from the registry.

## Fonts

Geist, Geist Mono and Doto are bundled under the SIL Open Font License. The licence texts are in `src/lib/resources/assets/cxvisuals/font`.

## Credits

- [Roughly Enough Items](https://github.com/shedaniel/RoughlyEnoughItems) inspired the item list: the searchable list beside the inventory, recipe and usage lookup, favourites, and the default R, U, A and O keys. No REI code is used.
- [NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) (MIT) supplies the SkyBlock item and recipe data. It is downloaded when needed and not shipped with the mod.
- [Geist](https://github.com/vercel/geist-font) by Vercel and [Doto](https://github.com/oliverlalan/Doto) by Oliver Lalan, under the SIL Open Font License.
- The idle camera takes its style from the idle camera in Forza Horizon.
- The interface sounds are synthesized for this project and are not taken from any sound pack.
- Lyrics come from [LRCLIB](https://lrclib.net), missing covers from [Deezer](https://www.deezer.com), and profiles and skins from Mojang and Microsoft services.
- Built on [Fabric](https://fabricmc.net) and works with [Sodium](https://github.com/CaffeineMC/sodium) and [Iris](https://github.com/IrisShaders/Iris).

If something here is yours and not credited, open an issue and it will be added.
