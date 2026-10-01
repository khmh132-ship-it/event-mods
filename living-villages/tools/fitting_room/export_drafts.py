"""Writes the generated drafts into the mod resources (data/livingvillages/structures/plains/).

Only drafts that have no file there yet are written, so edited versions brought in with
import_structures.py are never overwritten. Pass --force to regenerate all of them.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from drafts import DRAFTS  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'data', 'livingvillages',
                   'structures', 'plains')

if __name__ == '__main__':
    force = '--force' in sys.argv
    os.makedirs(OUT, exist_ok=True)
    for name, (build, _) in DRAFTS.items():
        path = os.path.join(OUT, name + '.nbt')
        if force or not os.path.exists(path):
            build().save(path)
            print('wrote', name)
