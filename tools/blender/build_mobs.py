"""
Builds tools/blender/NAME.blend for every realm creature from tools/blender/specs/NAME.json and renders previews.

    blender -b --factory-startup -P tools/blender/build_mobs.py -- [--rebuild] [--previews-only] [NAME ...]

Existing .blend files are kept (they are the source once made) unless --rebuild is given.
In each .blend:
  - every body part is an Empty named like the part (rotation mode XZY = Minecraft's Z*Y*X order),
    its textured cubes are a child mesh "NAME_mesh"
  - custom properties mc_pivot / mc_rest on each Empty hold the Minecraft rest pose
  - the animation clips sit one after another on the timeline between markers "clip" and "clip_end" (20 fps)
Edit the keyframes in Blender, save, then run export_mobs.py.
Axes: Minecraft (x, y down, z back) -> Blender (x, z, -y) / 16, so one Blender unit is one block.
"""
import json
import math
import os
import sys

import bpy

HERE = os.path.dirname(os.path.abspath(__file__))
SPECS = os.path.join(HERE, 'specs')
PREVIEWS = os.path.join(HERE, 'previews')
TEX = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'redstoneplus', 'textures', 'entity', 'realm')
FPS = 20
GAP = 10
CLIP_ORDER = ['idle', 'walk', 'attack', 'ability']


def mc_to_b(v):
    return (v[0] / 16.0, v[2] / 16.0, -v[1] / 16.0)


def rot_to_b(deg):
    a, b, c = (math.radians(x) for x in deg)
    return (a, c, -b)


def scale_to_b(s):
    return (s[0], s[2], s[1])


def material(name, tw, th):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes.get('Principled BSDF')
    img = bpy.data.images.load(os.path.join(TEX, name + '.png'))
    img.pack()
    glow = bpy.data.images.load(os.path.join(TEX, name + '_glow.png'))
    glow.pack()
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.interpolation = 'Closest'
    gtex = nt.nodes.new('ShaderNodeTexImage')
    gtex.image = glow
    gtex.interpolation = 'Closest'
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    nt.links.new(tex.outputs['Alpha'], bsdf.inputs['Alpha'])
    nt.links.new(gtex.outputs['Color'], bsdf.inputs['Emission Color'])
    bsdf.inputs['Emission Strength'].default_value = 3.0
    bsdf.inputs['Roughness'].default_value = 0.8
    if hasattr(mat, 'blend_method'):
        mat.blend_method = 'CLIP'
    return mat


def cube_mesh(name, cubes, tw, th, mat):
    verts, faces, uvs = [], [], []
    for c in cubes:
        ox, oy, oz = c['origin']
        w, h, d = c['size']
        inf = c.get('inflate', 0.0)
        x0, x1 = ox - inf, ox + w + inf
        y0, y1 = oy - inf, oy + h + inf
        z0, z1 = oz - inf, oz + d + inf
        u, v = c['uv']
        iw, ih, idd = (int(math.ceil(q)) for q in (w, h, d))

        def P(x, y, z):
            return mc_to_b((x, y, z))

        def UV(px, py):
            return (px / tw, 1.0 - py / th)

        # (4 corners in Minecraft space, 4 pixel corners) per face; corners are ordered so the normal points out
        quads = [
            # top (min y): texture (u+d, v) w x d
            ([(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
             [(u + idd, v + idd), (u + idd + iw, v + idd), (u + idd + iw, v), (u + idd, v)]),
            # bottom (max y): (u+d+w, v)
            ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
             [(u + idd + iw, v), (u + idd + 2 * iw, v), (u + idd + 2 * iw, v + idd), (u + idd + iw, v + idd)]),
            # front (min z): (u+d, v+d) w x h
            ([(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)],
             [(u + idd, v + idd + ih), (u + idd + iw, v + idd + ih), (u + idd + iw, v + idd), (u + idd, v + idd)]),
            # back (max z): (u+2d+w, v+d)
            ([(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)],
             [(u + 2 * idd + iw, v + idd + ih), (u + 2 * idd + 2 * iw, v + idd + ih), (u + 2 * idd + 2 * iw, v + idd), (u + 2 * idd + iw, v + idd)]),
            # right side (min x): (u, v+d) d x h
            ([(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)],
             [(u, v + idd + ih), (u + idd, v + idd + ih), (u + idd, v + idd), (u, v + idd)]),
            # left side (max x): (u+d+w, v+d)
            ([(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)],
             [(u + idd + iw, v + idd + ih), (u + 2 * idd + iw, v + idd + ih), (u + 2 * idd + iw, v + idd), (u + idd + iw, v + idd)]),
        ]
        for corners, pix in quads:
            base = len(verts)
            # Minecraft space is mirrored against Blender's (y flips), so reverse the winding to keep normals outside
            order = [0, 3, 2, 1]
            for k in order:
                verts.append(P(*corners[k]))
                uvs.append(UV(*pix[k]))
            faces.append([base, base + 1, base + 2, base + 3])
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    uv_layer = mesh.uv_layers.new(name='UVMap')
    for poly in mesh.polygons:
        for li in poly.loop_indices:
            uv_layer.data[li].uv = uvs[mesh.loops[li].vertex_index]
    mesh.materials.append(mat)
    # every cube is closed, so let Blender point all normals outwards
    import bmesh
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    mesh.update()
    return mesh


def build(spec, path):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.fps = FPS
    name = spec['name']
    tw, th = spec['texture_size']
    mat = material(name, tw, th)
    empties = {}
    for p in spec['parts']:
        e = bpy.data.objects.new(p['name'], None)
        e.empty_display_type = 'PLAIN_AXES'
        e.empty_display_size = 0.15
        e.rotation_mode = 'XZY'
        e.location = mc_to_b(p['pivot'])
        e.rotation_euler = rot_to_b(p['rotation'])
        e['mc_pivot'] = p['pivot']
        e['mc_rest'] = p['rotation']
        scene.collection.objects.link(e)
        if p['parent'] in empties:
            e.parent = empties[p['parent']]
        empties[p['name']] = e
        if p['cubes']:
            m = bpy.data.objects.new(p['name'] + '_mesh', cube_mesh(p['name'] + '_mesh', p['cubes'], tw, th, mat))
            scene.collection.objects.link(m)
            m.parent = e
    # the model's ground is y = 24: lift everything so the feet stand on Blender's z = 0
    root = bpy.data.objects.new('root', None)
    root.empty_display_type = 'ARROWS'
    root.location = (0, 0, 24 / 16.0)
    scene.collection.objects.link(root)
    for p in spec['parts']:
        if p['parent'] not in empties:
            empties[p['name']].parent = root

    # animation clips on the timeline
    frame = 0
    clips = {}
    anims = spec['animations']
    names = [c for c in CLIP_ORDER if c in anims] + [c for c in anims if c not in CLIP_ORDER]
    for clip_name in names:
        clip = anims[clip_name]
        start = frame
        end = start + int(round(clip['length'] * FPS))
        clips[clip_name] = {'loop': clip['loop'], 'start': start, 'end': end}
        scene.timeline_markers.new(clip_name, frame=start)
        scene.timeline_markers.new(clip_name + '_end', frame=end)
        for p in spec['parts']:
            e = empties[p['name']]
            rest_loc = mc_to_b(p['pivot'])
            rest_rot = p['rotation']
            bone = clip['bones'].get(p['name'], {})
            for channel in ('rotation', 'position', 'scale'):
                ks = bone.get(channel)
                data_path = {'rotation': 'rotation_euler', 'position': 'location', 'scale': 'scale'}[channel]
                if not ks:
                    # hold the rest pose over this clip so clips do not bleed into each other
                    for f in (start, end):
                        if channel == 'rotation':
                            e.rotation_euler = rot_to_b(rest_rot)
                        elif channel == 'position':
                            e.location = rest_loc
                        else:
                            e.scale = (1, 1, 1)
                        e.keyframe_insert(data_path, frame=f)
                    continue
                for t, x, y, z in ks:
                    f = start + t * FPS
                    if channel == 'rotation':
                        e.rotation_euler = rot_to_b([rest_rot[0] + x, rest_rot[1] + y, rest_rot[2] + z])
                    elif channel == 'position':
                        d = mc_to_b((x, y, z))
                        e.location = (rest_loc[0] + d[0], rest_loc[1] + d[1], rest_loc[2] + d[2])
                    else:
                        e.scale = scale_to_b((x, y, z))
                    e.keyframe_insert(data_path, frame=f)
        frame = end + GAP
    scene.frame_start = 0
    scene.frame_end = frame - GAP
    meta = {k: spec[k] for k in ('name', 'texture_size', 'look', 'shadow', 'scale')}
    meta['cubes'] = {p['name']: [{k: v for k, v in c.items() if k != 'mat'} for c in p['cubes']] for p in spec['parts']}
    meta['parents'] = {p['name']: p['parent'] for p in spec['parts']}
    meta['order'] = [p['name'] for p in spec['parts']]
    scene['mc_spec'] = json.dumps(meta)
    scene['mc_clips'] = json.dumps(clips)
    bpy.ops.wm.save_as_mainfile(filepath=path)


def previews(name):
    """Renders one pose from each clip (Workbench, textured) to tools/blender/previews/NAME_CLIP.png."""
    scene = bpy.context.scene
    clips = json.loads(scene['mc_clips'])
    scene.render.engine = 'BLENDER_WORKBENCH'
    scene.display.shading.light = 'STUDIO'
    scene.display.shading.color_type = 'TEXTURE'
    scene.display.shading.show_cavity = True
    scene.render.film_transparent = True
    scene.render.resolution_x = 480
    scene.render.resolution_y = 480
    for obj in scene.objects:
        if obj.type == 'EMPTY':
            obj.hide_render = True
    cam_data = bpy.data.cameras.new('preview_cam')
    cam_data.type = 'ORTHO'
    cam = bpy.data.objects.new('preview_cam', cam_data)
    scene.collection.objects.link(cam)
    scene.camera = cam
    target = bpy.data.objects.new('preview_target', None)
    scene.collection.objects.link(target)
    track = cam.constraints.new('TRACK_TO')
    track.target = target
    track.track_axis = 'TRACK_NEGATIVE_Z'
    track.up_axis = 'UP_Y'
    os.makedirs(PREVIEWS, exist_ok=True)
    meshes = [o for o in scene.objects if o.type == 'MESH']
    for clip_name, c in clips.items():
        frame = c['start'] + (c['end'] - c['start']) // (4 if clip_name in ('attack', 'ability') else 3)
        scene.frame_set(frame)
        lo = [1e9, 1e9, 1e9]
        hi = [-1e9, -1e9, -1e9]
        for m in meshes:
            for corner in m.bound_box:
                w = m.matrix_world @ __import__('mathutils').Vector(corner)
                for k in range(3):
                    lo[k] = min(lo[k], w[k])
                    hi[k] = max(hi[k], w[k])
        center = [(lo[k] + hi[k]) / 2 for k in range(3)]
        size = max(hi[k] - lo[k] for k in range(3))
        target.location = center
        cam.location = (center[0] - size * 1.4, center[1] - size * 2.0, center[2] + size * 1.0)
        cam_data.ortho_scale = size * 1.45
        scene.render.filepath = os.path.join(PREVIEWS, f'{name}_{clip_name}.png')
        bpy.ops.render.render(write_still=True)


def main():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    rebuild = '--rebuild' in argv
    previews_only = '--previews-only' in argv
    names = [a for a in argv if not a.startswith('--')]
    if not names:
        names = sorted(f[:-5] for f in os.listdir(SPECS) if f.endswith('.json'))
    for name in names:
        path = os.path.join(HERE, name + '.blend')
        if not previews_only and (rebuild or not os.path.exists(path)):
            with open(os.path.join(SPECS, name + '.json'), encoding='utf-8') as f:
                build(json.load(f), path)
            print('BUILT', name)
        bpy.ops.wm.open_mainfile(filepath=path)
        previews(name)
        print('PREVIEWED', name)


main()
