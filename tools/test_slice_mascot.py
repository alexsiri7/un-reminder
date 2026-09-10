"""Tests for tools/slice_mascot.py.

Fixtures are generated in-test so nothing here depends on art/mascot-sheet-v1.png.
"""

import json
import sys
from pathlib import Path

import pytest
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))

from slice_mascot import boundaries, emit_kotlin, main  # noqa: E402


def cell_colour(number: int) -> tuple[int, int, int]:
    return (number * 7 % 256, number * 13 % 256, number * 29 % 256)


def make_sheet(path: Path, cols: int, rows: int, width: int, height: int) -> Path:
    """A sheet whose every grid cell is a unique solid colour."""
    sheet = Image.new("RGB", (width, height))
    xs = boundaries(width, cols)
    ys = boundaries(height, rows)
    for index in range(cols * rows):
        row, col = divmod(index, cols)
        block = Image.new(
            "RGB", (xs[col + 1] - xs[col], ys[row + 1] - ys[row]), cell_colour(index + 1)
        )
        sheet.paste(block, (xs[col], ys[row]))
    sheet.save(path)
    return path


def run(sheet: Path, tmp_path: Path, *extra: str) -> int:
    return main(
        [
            str(sheet),
            "--cols",
            "6",
            "--rows",
            "4",
            "--expect-size",
            f"{Image.open(sheet).width}x{Image.open(sheet).height}",
            "--out-dir",
            str(tmp_path / "out"),
            "--catalogue",
            str(tmp_path / "art" / "mascot-sprites.json"),
            *extra,
        ]
    )


def assert_shows_cell(path: Path, number: int) -> None:
    """The tile's dominant colour identifies which grid cell it came from.

    Compared with a tolerance because the tiles are saved as lossy WebP.
    """
    with Image.open(path) as image:
        tile = image.convert("RGB")
        dominant = max(tile.getcolors(tile.width * tile.height))[1]
    expected = cell_colour(number)
    assert all(abs(a - b) <= 4 for a, b in zip(dominant, expected)), (
        f"{path.name} shows {dominant}, expected cell {number} colour {expected}"
    )


def test_tile_count_and_reading_order(tmp_path):
    sheet = make_sheet(tmp_path / "sheet.png", 6, 4, 600, 400)

    assert run(sheet, tmp_path) == 0

    out = tmp_path / "out"
    assert sorted(p.name for p in out.iterdir()) == [
        f"mascot_{n:02d}.webp" for n in range(1, 25)
    ]
    for number in range(1, 25):
        assert_shows_cell(out / f"mascot_{number:02d}.webp", number)


def test_boundaries_exactly_cover_a_width_that_does_not_divide_evenly():
    assert boundaries(13, 6) == [0, 2, 4, 7, 9, 11, 13]
    # The real sheet: 1376 % 6 == 2, and these are its measured seams.
    assert boundaries(1376, 6) == [0, 229, 459, 688, 917, 1147, 1376]

    for total, n in ((13, 6), (1376, 6), (768, 4), (100, 7), (33, 32)):
        xs = boundaries(total, n)
        assert len(xs) == n + 1
        assert xs[0] == 0 and xs[-1] == total
        assert all(a < b for a, b in zip(xs, xs[1:]))
        covered = [0] * total
        for a, b in zip(xs, xs[1:]):
            for pixel in range(a, b):
                covered[pixel] += 1
        assert covered == [1] * total, "every pixel lands in exactly one tile"


def test_exclusions_are_honoured_without_renumbering(tmp_path):
    sheet = make_sheet(tmp_path / "sheet.png", 6, 4, 600, 400)

    assert run(sheet, tmp_path, "--skip", "8") == 0

    out = tmp_path / "out"
    assert not (out / "mascot_08.webp").exists()
    assert len(list(out.iterdir())) == 23
    # Survivors keep their positional numbers rather than closing the gap.
    assert_shows_cell(out / "mascot_09.webp", 9)

    entries = json.loads((tmp_path / "art" / "mascot-sprites.json").read_text())
    assert len(entries) == 23
    assert all(entry["file"] != "mascot_08.webp" for entry in entries)


def test_size_mismatch_exits_non_zero_and_writes_nothing(tmp_path):
    sheet = make_sheet(tmp_path / "sheet.png", 6, 4, 600, 400)
    out = tmp_path / "out"

    code = main(
        [
            str(sheet),
            "--cols", "6",
            "--rows", "4",
            "--expect-size", "1376x768",
            "--out-dir", str(out),
            "--catalogue", str(tmp_path / "art" / "mascot-sprites.json"),
        ]
    )

    assert code != 0
    assert not out.exists()
    assert not (tmp_path / "art" / "mascot-sprites.json").exists()


@pytest.mark.parametrize("bad", ["1376", "1376x", "wide x tall", "1376x768x2", "0x768"])
def test_unparseable_expect_size_exits_non_zero(tmp_path, bad):
    sheet = make_sheet(tmp_path / "sheet.png", 6, 4, 600, 400)

    assert main([str(sheet), "--cols", "6", "--rows", "4", "--expect-size", bad]) != 0


def test_tiles_below_the_minimum_edge_exit_non_zero(tmp_path):
    sheet = make_sheet(tmp_path / "sheet.png", 6, 4, 600, 400)
    out = tmp_path / "out"

    code = main(
        [
            str(sheet),
            "--cols", "60",
            "--rows", "4",
            "--expect-size", "600x400",
            "--out-dir", str(out),
        ]
    )

    assert code != 0
    assert not out.exists()


def test_grid_and_exclusions_come_from_arguments(tmp_path):
    sheet = make_sheet(tmp_path / "sheet.png", 3, 2, 300, 200)
    out = tmp_path / "out"

    code = main(
        [
            str(sheet),
            "--cols", "3",
            "--rows", "2",
            "--expect-size", "300x200",
            "--skip", "2", "5",
            "--out-dir", str(out),
            "--catalogue", str(tmp_path / "art" / "mascot-sprites.json"),
        ]
    )

    assert code == 0
    assert sorted(p.name for p in out.iterdir()) == [
        "mascot_01.webp",
        "mascot_03.webp",
        "mascot_04.webp",
        "mascot_06.webp",
    ]


def test_catalogue_is_never_clobbered(tmp_path):
    sheet = make_sheet(tmp_path / "sheet.png", 6, 4, 600, 400)
    catalogue = tmp_path / "art" / "mascot-sprites.json"

    assert run(sheet, tmp_path) == 0
    reviewed = json.loads(catalogue.read_text())
    reviewed[0]["tag"] = "astronaut_floating"
    reviewed[0]["description"] = "a cat floating in a spacesuit"
    catalogue.write_text(json.dumps(reviewed, indent=2) + "\n")
    before = catalogue.read_bytes()

    assert run(sheet, tmp_path) == 0

    assert catalogue.read_bytes() == before
    assert json.loads(catalogue.read_text())[0]["tag"] == "astronaut_floating"


def test_kotlin_emission_escapes_and_strips_the_suffix():
    block = emit_kotlin(
        [
            {
                "file": "mascot_01.webp",
                "tag": "chef_flipping",
                "description": r'a "chef" with $5 and a \ mark',
            }
        ]
    )

    assert "R.drawable.mascot_01" in block
    assert ".webp" not in block
    assert r"a \"chef\" with \$5 and a \\ mark" in block
    assert block.startswith("// Generated by tools/slice_mascot.py")
