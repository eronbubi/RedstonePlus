package de.eron.redstoneplus.realm.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.eron.redstoneplus.realm.RealmAnimated;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model of a realm creature, built from the JSON that tools/blender/export_mobs.py writes out of the creature's
 * .blend file (assets/redstoneplus/realm_models/NAME.json): the part hierarchy with its cubes, and keyframed
 * clips "idle", "walk", "attack" and "ability". Clips are added on top of the rest pose:
 * idle loops on age, walk follows the limb swing and fades in with speed, attack follows the swing progress and
 * ability starts when the entity sends {@link RealmAnimated#ABILITY_EVENT}.
 */
public class RealmModel<T extends LivingEntity> extends HierarchicalModel<T> {
    private static final Map<String, Spec> SPECS = new HashMap<>();
    /** limbSwing units per walk cycle (vanilla legs use cos(limbSwing * 0.6662)). */
    private static final float WALK_CYCLE = Mth.TWO_PI / 0.6662F;

    private final ModelPart root;
    private final Spec spec;
    private final Map<String, ModelPart> parts = new HashMap<>();

    public RealmModel(ModelPart root, String name) {
        this.root = root;
        this.spec = spec(name);
        for (PartSpec part : this.spec.parts) {
            this.getAnyDescendantWithName(part.name).ifPresent(p -> this.parts.put(part.name, p));
        }
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.getAllParts().forEach(part -> {
            part.resetPose();
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
        });
        float seconds = ageInTicks / 20.0F;

        this.play("idle", seconds, 1.0F);
        float walkWeight = Mth.clamp(limbSwingAmount * 1.6F, 0.0F, 1.0F);
        if (walkWeight > 0.01F) {
            Clip walk = this.spec.clips.get("walk");
            if (walk != null) {
                this.apply(walk, (limbSwing / WALK_CYCLE) * walk.length, walkWeight);
            }
        }
        if (this.attackTime > 0.0F) {
            Clip attack = this.spec.clips.get("attack");
            if (attack != null) {
                this.apply(attack, this.attackTime * attack.length, 1.0F);
            }
        }
        if (entity instanceof RealmAnimated animated) {
            Clip ability = this.spec.clips.get("ability");
            if (ability != null) {
                float override = animated.abilityOverride(ageInTicks - entity.tickCount);
                if (override >= 0.0F) {
                    this.apply(ability, override * ability.length, 1.0F);
                } else {
                    float t = (ageInTicks - animated.abilityStart()) / 20.0F;
                    if (t >= 0.0F && t <= ability.length) {
                        this.apply(ability, t, 1.0F);
                    }
                }
            }
        }
        for (String name : this.spec.lookParts) {
            ModelPart part = this.parts.get(name);
            if (part != null) {
                part.yRot += netHeadYaw * Mth.DEG_TO_RAD / this.spec.lookParts.size();
                part.xRot += headPitch * Mth.DEG_TO_RAD / this.spec.lookParts.size();
            }
        }
    }

    private void play(String clip, float time, float weight) {
        Clip c = this.spec.clips.get(clip);
        if (c != null) {
            this.apply(c, time, weight);
        }
    }

    private void apply(Clip clip, float time, float weight) {
        float t = clip.loop ? Mth.positiveModulo(time, clip.length) : Mth.clamp(time, 0.0F, clip.length);
        for (Track track : clip.tracks) {
            ModelPart part = this.parts.get(track.part);
            if (part == null) {
                continue;
            }
            if (track.rotation != null) {
                float[] r = sample(track.rotation, t);
                part.xRot += r[0] * Mth.DEG_TO_RAD * weight;
                part.yRot += r[1] * Mth.DEG_TO_RAD * weight;
                part.zRot += r[2] * Mth.DEG_TO_RAD * weight;
            }
            if (track.position != null) {
                float[] p = sample(track.position, t);
                part.x += p[0] * weight;
                part.y += p[1] * weight;
                part.z += p[2] * weight;
            }
            if (track.scale != null) {
                float[] s = sample(track.scale, t);
                part.xScale *= 1.0F + (s[0] - 1.0F) * weight;
                part.yScale *= 1.0F + (s[1] - 1.0F) * weight;
                part.zScale *= 1.0F + (s[2] - 1.0F) * weight;
            }
        }
    }

    /** Keyframes are [time, x, y, z]; Catmull-Rom spline through them, so sampled curves stay smooth. */
    private static float[] sample(float[][] keys, float t) {
        if (t <= keys[0][0]) {
            return new float[]{keys[0][1], keys[0][2], keys[0][3]};
        }
        for (int i = 1; i < keys.length; i++) {
            if (t <= keys[i][0]) {
                float[] p0 = keys[Math.max(0, i - 2)];
                float[] p1 = keys[i - 1];
                float[] p2 = keys[i];
                float[] p3 = keys[Math.min(keys.length - 1, i + 1)];
                float f = (t - p1[0]) / Math.max(1.0E-4F, p2[0] - p1[0]);
                float[] out = new float[3];
                for (int k = 1; k <= 3; k++) {
                    out[k - 1] = Mth.catmullrom(f, p0[k], p1[k], p2[k], p3[k]);
                }
                return out;
            }
        }
        float[] last = keys[keys.length - 1];
        return new float[]{last[1], last[2], last[3]};
    }

    // ---------------------------------------------------------------------------------------------------- spec

    public static LayerDefinition layer(String name) {
        Spec spec = spec(name);
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        defs.put("root", mesh.getRoot());
        for (PartSpec part : spec.parts) {
            CubeListBuilder cubes = CubeListBuilder.create();
            for (CubeSpec cube : part.cubes) {
                cubes.texOffs(cube.u, cube.v).mirror(cube.mirror)
                        .addBox(cube.x, cube.y, cube.z, cube.w, cube.h, cube.d, new CubeDeformation(cube.inflate));
            }
            PartDefinition parent = defs.getOrDefault(part.parent, mesh.getRoot());
            defs.put(part.name, parent.addOrReplaceChild(part.name, cubes, PartPose.offsetAndRotation(
                    part.pivot[0], part.pivot[1], part.pivot[2],
                    part.rotation[0] * Mth.DEG_TO_RAD, part.rotation[1] * Mth.DEG_TO_RAD, part.rotation[2] * Mth.DEG_TO_RAD)));
        }
        return LayerDefinition.create(mesh, spec.texW, spec.texH);
    }

    public static Spec spec(String name) {
        return SPECS.computeIfAbsent(name, RealmModel::load);
    }

    private static Spec load(String name) {
        String path = "/assets/redstoneplus/realm_models/" + name + ".json";
        try (InputStream in = RealmModel.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing realm model " + path);
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return Spec.parse(json);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read realm model " + path, e);
        }
    }

    record CubeSpec(int u, int v, float x, float y, float z, float w, float h, float d, float inflate, boolean mirror) {
    }

    record PartSpec(String name, String parent, float[] pivot, float[] rotation, List<CubeSpec> cubes) {
    }

    record Track(String part, float[][] rotation, float[][] position, float[][] scale) {
    }

    record Clip(float length, boolean loop, List<Track> tracks) {
    }

    public record Spec(int texW, int texH, List<PartSpec> parts, Map<String, Clip> clips, List<String> lookParts,
                       float shadow, float scale) {
        static Spec parse(JsonObject json) {
            JsonArray tex = json.getAsJsonArray("texture_size");
            List<PartSpec> parts = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("parts")) {
                JsonObject p = e.getAsJsonObject();
                List<CubeSpec> cubes = new ArrayList<>();
                for (JsonElement c : p.getAsJsonArray("cubes")) {
                    JsonObject o = c.getAsJsonObject();
                    float[] origin = floats(o.getAsJsonArray("origin"));
                    float[] size = floats(o.getAsJsonArray("size"));
                    JsonArray uv = o.getAsJsonArray("uv");
                    cubes.add(new CubeSpec(uv.get(0).getAsInt(), uv.get(1).getAsInt(), origin[0], origin[1], origin[2],
                            size[0], size[1], size[2], o.has("inflate") ? o.get("inflate").getAsFloat() : 0.0F,
                            o.has("mirror") && o.get("mirror").getAsBoolean()));
                }
                parts.add(new PartSpec(p.get("name").getAsString(), p.has("parent") ? p.get("parent").getAsString() : "root",
                        floats(p.getAsJsonArray("pivot")), p.has("rotation") ? floats(p.getAsJsonArray("rotation")) : new float[3], cubes));
            }
            Map<String, Clip> clips = new HashMap<>();
            JsonObject anims = json.getAsJsonObject("animations");
            if (anims != null) {
                for (Map.Entry<String, JsonElement> entry : anims.entrySet()) {
                    JsonObject a = entry.getValue().getAsJsonObject();
                    List<Track> tracks = new ArrayList<>();
                    for (Map.Entry<String, JsonElement> bone : a.getAsJsonObject("bones").entrySet()) {
                        JsonObject b = bone.getValue().getAsJsonObject();
                        tracks.add(new Track(bone.getKey(), keys(b, "rotation"), keys(b, "position"), keys(b, "scale")));
                    }
                    clips.put(entry.getKey(), new Clip(a.get("length").getAsFloat(), a.has("loop") && a.get("loop").getAsBoolean(), tracks));
                }
            }
            List<String> look = new ArrayList<>();
            if (json.has("look")) {
                json.getAsJsonArray("look").forEach(e -> look.add(e.getAsString()));
            }
            return new Spec(tex.get(0).getAsInt(), tex.get(1).getAsInt(), parts, clips, look,
                    json.has("shadow") ? json.get("shadow").getAsFloat() : 0.6F, json.has("scale") ? json.get("scale").getAsFloat() : 1.0F);
        }

        private static float[][] keys(JsonObject bone, String channel) {
            if (!bone.has(channel)) {
                return null;
            }
            JsonArray arr = bone.getAsJsonArray(channel);
            float[][] out = new float[arr.size()][];
            for (int i = 0; i < arr.size(); i++) {
                out[i] = floats(arr.get(i).getAsJsonArray());
            }
            return out.length == 0 ? null : out;
        }

        private static float[] floats(JsonArray arr) {
            float[] out = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                out[i] = arr.get(i).getAsFloat();
            }
            return out;
        }
    }
}
