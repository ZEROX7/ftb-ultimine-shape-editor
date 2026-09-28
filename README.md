# FTB Ultimine Shape Editor (NeoForge 26.1)

> This is the **Minecraft 26.1** branch (26.1, 26.1.1 and 26.1.2). The Minecraft 1.21.1 version lives on the `main` branch.

An addon for FTB Ultimine that lets every player draw their own Ultimine shapes in-game.

by **[ZEROX7](https://github.com/ZEROX7)** · [Source](https://github.com/ZEROX7/ftb-ultimine-shape-editor) · [Report an issue](https://github.com/ZEROX7/ftb-ultimine-shape-editor/issues)

- Up to **10 custom shapes** per player, stored on the server with the player (they survive relogs and deaths).
- Only the slots you've actually made a shape in appear in Ultimine's shape menu and when cycling shapes.
  Empty slots are invisible, so without any custom shapes Ultimine looks and behaves exactly as before.
- Your shape names show up in Ultimine's shape menu.

## Using the editor

Open it with **K** (rebindable under Controls → FTB Ultimine) or the `/shapeeditor` command.

- **Slots 1–10** are the numbered tabs at the top. Green numbers have a shape and show in Ultimine; grey ones are
  empty and hidden. Give the shape a name in the box below.
- The grid is the face you mine, as you see it: the top of the grid is the top of your screen, and the gold
  cell is the block you mine. Left-click or drag to add/remove cells, right-click to remove.
- **Layers** go deeper into the block (up to 15). Use `<` `>` or scroll over the grid. Faint squares show the
  previous layer; **Copy prev** repeats it.
- The grid is 15×15 (7 blocks out from the centre in each direction).
- **Repeating:** go to a layer and press **Repeat from here**. Everything from that layer to your last drawn
  layer then repeats forever, deeper and deeper into the block, until a whole repeat has nothing left to mine
  (like Ultimine's own tunnels, and still limited by `max_blocks`). Layers before it are mined once, so you can
  build e.g. a wide entrance followed by an endless tunnel. Press the button again on that layer to stop repeating.
  - **In loop** (for layers before the repeat start): set it to *yes* to put that layer into every repeat too.
    The loop is then all "in loop" layers in their normal order, e.g. layers 1, 3 and 4 in the loop gives
    1‑2‑3‑4 once, then 1‑3‑4, 1‑3‑4, … Layers set to *no* are mined only once at the start.
  - Draw only layer 1 and repeat from it: a tunnel of that cross-section.
  - Repeat a 3-layer section: a pattern that is 3 layers long, looped.
- **Shift → / Shift ↑** (−7 to +7) move each repeat compared to the one before. On a one-layer loop that's
  layer-to-layer, so Shift ↑ +1 makes a staircase going up, Shift → +1 a diagonal tunnel. Orange dots in the grid show
  where the current layer lands in the next repeat, and repeating layers get a cyan frame and a ⟳.
- **Max depth** (optional, off by default): limits how many layers deep the shape goes, e.g. an endless tunnel
  that stops after 32 blocks. Use `-` / `+` (Shift-click for 10 at a time); turning it on starts at the depth
  you've drawn, and going below 1 turns it off. Layers past the limit are marked red in the editor.
- **Reset** deletes the shape in this slot, which hides it from Ultimine again.
- **Save** stores your shapes. **Cancel** / Esc asks once before throwing away unsaved changes.
- **Copy code / Paste code** puts a text code on your clipboard to share a shape with friends.

The same shape works on walls, floors and ceilings. On a floor the top of the grid points the way you face,
on a ceiling it points behind you, which is how it looks on screen in both cases.

## How it works

Ultimine's list of shapes is fixed when the game starts, so the mod registers 10 "custom slot" shapes once; what
each one mines is looked up from the mining player's own saved shapes. Two small mixins into FTB Ultimine hide the
slots a player hasn't defined: one skips them when the server cycles your shape, the other leaves them out of
the shape menu on your screen.

## Build

Needs **JDK 25** and an internet connection.

```
./gradlew build        (Windows: gradlew.bat build)
```

The jar lands in `build/libs/`. Put it in your `mods` folder alongside FTB Ultimine and FTB Library (26.1 builds).
It must be installed on **both** the client and the server.

`./gradlew runClient` starts a dev game with Ultimine loaded, for testing.

## Releasing (GitHub Actions → CurseForge)

`.github/workflows/build.yml` on this branch builds the mod on every push and publishes a release for a tag that
starts with **`26.1-`**, e.g. `26.1-1.0.1` (plain tags like `1.0.1` belong to the 1.21.1 branch):

- **From IntelliJ:** Git → New Tag… (`26.1-1.0.1`), then Git → Push… with **Push tags** ticked.
- **From GitHub:** Actions → *Build & Publish (26.1)* → **Run workflow**, pick this branch, type an existing tag.

The version comes from the tag (`26.1-1.0.1` → `1.0.1`), the changelog from the commit messages since the previous
`26.1-` tag (add `[skip changelog]` to a commit message to leave it out). The jar is uploaded to CurseForge for
Minecraft 26.1, 26.1.1 and 26.1.2 (with the same `mod-publish-plugin` FTB uses) and to a GitHub Release.
Tags containing `alpha` or `beta` are uploaded as alpha/beta files.

It uses the same repo secret `CURSEFORGE_TOKEN` and variable `CURSEFORGE_PROJECT_ID` as the 1.21.1 branch.

## Bugs and ideas

Found a bug or have an idea? Open an issue at
https://github.com/ZEROX7/ftb-ultimine-shape-editor/issues

## License

MIT. See [LICENSE](LICENSE).
