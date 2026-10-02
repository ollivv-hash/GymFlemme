#!/usr/bin/env python3
"""Converts the ANSES Ciqual table (xlsx) into the compact TSV embedded in the app.

Source: https://ciqual.anses.fr/ (Licence Ouverte / Etalab 2.0).
Attribution: Anses. 2025. Table de composition nutritionnelle des aliments Ciqual.

Usage: pip install openpyxl && python3 convert.py "Table Ciqual 2025_FR_2025_11_03.xlsx" ../../app/src/main/assets/ciqual.tsv

Value conventions in Ciqual: "-" = missing, "traces" = trace amount, "< x" = below detection limit.
Missing values stay empty (unknown); traces and "< x" become 0 (negligible for daily totals).
"""
import sys

import openpyxl

COLUMNS = {  # output field -> header prefix in the Ciqual sheet
    "kcal": "Energie,\nRèglement\nUE N°\n1169\n2011 (kcal",
    "protein": "Protéines,\nN x\nfacteur de\nJones",
    "carbs": "Glucides\n(g",
    "fat": "Lipides\n(g",
}


def parse(value):
    if value is None:
        return ""
    text = str(value).strip().replace(",", ".")
    if text in ("", "-"):
        return ""
    if text.lower() == "traces" or text.startswith("<"):
        return "0"
    try:
        number = float(text)
    except ValueError:
        return ""
    return f"{number:g}"


def main(source, target):
    sheet = openpyxl.load_workbook(source, read_only=True).worksheets[0]
    rows = sheet.iter_rows(values_only=True)
    header = list(next(rows))
    index = {key: next(i for i, h in enumerate(header) if h and str(h).startswith(prefix)) for key, prefix in COLUMNS.items()}
    code_i, name_i, group_i = header.index("alim_code"), header.index("alim_nom_fr"), header.index("alim_grp_nom_fr")
    count = 0
    with open(target, "w", encoding="utf-8", newline="\n") as out:
        out.write("code\tname\tgroup\tkcal\tprotein\tcarbs\tfat\n")
        for row in rows:
            if not row[code_i] or not row[name_i]:
                continue
            name = str(row[name_i]).strip().replace("\t", " ")
            group = str(row[group_i] or "").strip().replace("\t", " ")
            values = [parse(row[index[k]]) for k in ("kcal", "protein", "carbs", "fat")]
            out.write("\t".join([str(row[code_i]).strip(), name, group, *values]) + "\n")
            count += 1
    print(f"{count} foods written to {target}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
