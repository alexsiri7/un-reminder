# Mascot artwork

`mascot-sheet-v1.png` is a single AI-generated sheet of 24 mascot poses in a 6×4 grid.
`tools/slice_mascot.py` cuts it into the numbered WebP tiles in
`app/src/main/res/drawable-nodpi/`, and `mascot-sprites.json` is the reviewed catalogue
that names each one.

## Slicing the sheet

```sh
python3 tools/slice_mascot.py art/mascot-sheet-v1.png \
  --cols 6 --rows 4 --expect-size 1376x768 --skip 8
```

`--expect-size` is required and aborts the run if the sheet is not the size the grid was
worked out against — without it a replacement sheet would be sliced into nonsense.

## The two-pass workflow

1. **Slice.** The first run writes the tiles and a skeleton `mascot-sprites.json` with an
   empty `tag` and `description` per tile, then warns that nothing has been reviewed.
2. **Tag.** Open every written tile and fill in its `tag` (short snake_case: outfit plus
   action, as actually drawn) and `description` (a phrase the notification-variant model
   can reason about). Near-duplicates must be told apart by the tag alone — this sheet has
   three confusable groups: a costumed wizard vs. an uncostumed cat with a wand, an
   uncostumed cat with a sword vs. an armoured knight, and two cowboys vs. an uncostumed
   cat holding a rope.
3. **Re-run.** The same command leaves the reviewed catalogue untouched and prints the
   Kotlin sprite list for the notification code to paste.

The catalogue is never overwritten. To regenerate a skeleton, delete
`art/mascot-sprites.json` first — which discards every reviewed tag.

## Swapping in a new sheet

Commit it as `art/mascot-sheet-vN.png` and pass the new `--cols`, `--rows`,
`--expect-size` and `--skip`. Grid shape and exclusions are arguments, so no code changes.
The slicer warns when the on-disk catalogue no longer matches the tiles just written.

## What the tiles look like

Tiles are **not square**: this sheet cuts to 229–230 × 192 px (about 1.19 : 1), because
1376 is not a multiple of 6. Boundaries are rounded to the sheet's real seams so the tiles
cover it exactly, with nothing cropped or padded. Tiles are never upscaled; the long edge
is only reduced when it exceeds 256 px, which does not happen here.

Known and accepted: the sheet's row seams sit at y ≈ 191 and 577 rather than exactly 192
and 576, so a few tiles carry one blended pixel from a neighbouring cell along one edge.
That is one row out of 192 on an image Android renders inside a circular crop. Not a
defect — no inset or trim flag is wanted.

## The IP constraint

No sprite may depict a character, costume or emblem owned by anyone else. On this sheet,
tile 08 is a cat in a Superman costume — cape, blue suit, S-shield — so it is excluded via
`--skip 8` and never written. That is what the exclusion list is for: review every tile of
a new sheet for the same problem before shipping it.
