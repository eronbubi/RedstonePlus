"""
Exports the realm creatures from their .blend files to the JSON the game loads
(src/main/resources/assets/redstoneplus/realm_models/NAME.json).

    blender -b --factory-startup -P tools/blender/export_mobs.py -- [NAME ...]

What is read from the .blend:
  - the animation of every part Empty, sampled at 20 fps between the timeline markers "clip" and "clip_end"
    (rotation, location and scale, relative to the rest pose in the Empty's mc_rest / mc_pivot properties)
  - the rest pose (mc_pivot / mc_rest) of every part
The cubes and their UVs come from the scene property mc_spec (written by build_mobs.py).
"""
import json
import math
import os
import sys

import bpy

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'redstoneplus', 'realm_models')
FPS = 20
TOL = {'rotation': 0.35, 'position': 0.04, 'scale': 0.004}


def b_to_mc_rot(e):
    a, c, mb = (math.degrees(v) for v in e)
    return (a, -mb, c)


def b_to_mc_pos(v):
    return (v[0] * 16.0, -v[2] * 16.0, v[1] * 16.0)


def simplify(keys, tol):
    """Drops keys that linear interpolation between their neighbours reproduces within tol."""
    if len(keys) <= 2:
        return keys
    out = [keys[0]]
    for i in range(1, len(keys) - 1):
        a, b, c = out[-1], keys[i], keys[i + 1]
        f = (b[0] - a[0]) / max(1e-6, c[0] - a[0])
        if any(abs(a[k] + (c[k] - a[k]) * f - b[k]) > tol for k in (1, 2, 3)):
            out.append(b)
    out.append(keys[-1])
    return out


def export(name):
    scene = bpy.context.scene
    meta = json.loads(scene['mc_spec'])
    clips = {}
    markers = {m.name: m.frame for m in scene.timeline_markers}
    loops = json.loads(scene['mc_clips']) if 'mc_clips' in scene else {}
    parts = [o for o in scene.objects if o.type == 'EMPTY' and 'mc_rest' in o]
    for clip_name, start in markers.items():
        if clip_name.endswith('_end') or clip_name + '_end' not in markers:
            continue
        end = markers[clip_name + '_end']
        tracks = {p.name: {'rotation': [], 'position': [], 'scale': []} for p in parts}
        for f in range(start, end + 1):
            scene.frame_set(f)
            t = round((f - start) / FPS, 3)
            for p in parts:
                rest = list(p['mc_rest'])
                pivot = list(p['mc_pivot'])
                r = b_to_mc_rot(p.rotation_euler)
                pos = b_to_mc_pos(p.location)
                s = p.scale
                tracks[p.name]['rotation'].append([t] + [round(r[k] - rest[k], 2) for k in range(3)])
                tracks[p.name]['position'].append([t] + [round(pos[k] - pivot[k], 3) for k in range(3)])
                tracks[p.name]['scale'].append([t, round(s[0], 4), round(s[2], 4), round(s[1], 4)])
        bones = {}
        for part_name, chans in tracks.items():
            bone = {}
            for chan, ks in chans.items():
                neutral = 1.0 if chan == 'scale' else 0.0
                if all(abs(k[i] - neutral) <= TOL[chan] for k in ks for i in (1, 2, 3)):
                    continue
                bone[chan] = simplify(ks, TOL[chan])
            if bone:
                bones[part_name] = bone
        clips[clip_name] = {'length': round((end - start) / FPS, 3), 'loop': loops.get(clip_name, {}).get('loop', clip_name in ('idle', 'walk')),
                            'bones': bones}
    by_name = {p.name: p for p in parts}
    out_parts = []
    for part_name in meta['order']:
        p = by_name.get(part_name)
        out_parts.append({
            'name': part_name, 'parent': meta['parents'][part_name],
            'pivot': list(p['mc_pivot']) if p else [0, 0, 0],
            'rotation': list(p['mc_rest']) if p else [0, 0, 0],
            'cubes': meta['cubes'][part_name],
        })
    game = {'name': name, 'texture_size': meta['texture_size'], 'parts': out_parts, 'animations': clips,
            'look': meta['look'], 'shadow': meta['shadow'], 'scale': meta['scale'], 'source': f'tools/blender/{name}.blend'}
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf-8') as f:
        json.dump(game, f, separators=(',', ':'))
    return sum(len(c['bones']) for c in clips.values())


def main():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    names = [a for a in argv if not a.startswith('--')]
    if not names:
        names = sorted(f[:-6] for f in os.listdir(HERE) if f.endswith('.blend'))
    for name in names:
        bpy.ops.wm.open_mainfile(filepath=os.path.join(HERE, name + '.blend'))
        tracks = export(name)
        print('EXPORTED', name, tracks, 'animated tracks')


main()
