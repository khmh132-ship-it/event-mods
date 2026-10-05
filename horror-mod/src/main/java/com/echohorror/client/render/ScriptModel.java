package com.echohorror.client.render;

import com.echohorror.EchoHorror;
import com.echohorror.entity.PhantomEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Not built like a person: stick limbs, arms past the knees, a small head, and black threads hanging from everything.
 * About three and a half blocks tall.
 */
public class ScriptModel extends HierarchicalModel<PhantomEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(EchoHorror.MODID, "script"), "main");
    private static final int THREADS = 22;

    private final ModelPart root, head, rightArm, leftArm, rightLeg, leftLeg, body;
    private final List<ModelPart> threads = new ArrayList<>();

    public ScriptModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        for (int i = 0; i < THREADS; i++) threads.add(findThread(root, "thread" + i));
    }

    private static ModelPart findThread(ModelPart root, String name) {
        ModelPart body = root.getChild("body");
        for (ModelPart p : new ModelPart[]{body, body.getChild("head"), body.getChild("right_arm"), body.getChild("left_arm")}) {
            if (p.hasChild(name)) return p.getChild(name);
        }
        return root.getChild("right_leg").hasChild(name) ? root.getChild("right_leg").getChild(name) : root.getChild("left_leg").getChild(name);
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        CubeListBuilder stick = CubeListBuilder.create().texOffs(0, 32);
        // legs: from the hip (y=-6) down to the ground (y=24)
        PartDefinition rl = r.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 32).addBox(-0.5f, 0f, -0.5f, 1, 30, 1), PartPose.offset(-1.5f, -6f, 0f));
        PartDefinition ll = r.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 32).addBox(-0.5f, 0f, -0.5f, 1, 30, 1), PartPose.offset(1.5f, -6f, 0f));
        // a narrow torso
        PartDefinition b = r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(8, 32).addBox(-2f, -16f, -1f, 4, 16, 2)
                .texOffs(0, 32).addBox(-0.5f, -20f, -0.5f, 1, 4, 1), PartPose.offset(0f, -6f, 0f));
        // a small head on a long neck, slightly too far forward
        PartDefinition h = b.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3f, -7f, -3f, 6, 7, 6), PartPose.offset(0f, -20f, -0.5f));
        // arms that end below the knees
        PartDefinition ra = b.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 32).addBox(-0.5f, 0f, -0.5f, 1, 36, 1), PartPose.offset(-2.6f, -15f, 0f));
        PartDefinition la = b.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 32).addBox(-0.5f, 0f, -0.5f, 1, 36, 1), PartPose.offset(2.6f, -15f, 0f));
        // threads hanging from everything
        Random rnd = new Random(1986);
        PartDefinition[] parents = {h, h, h, h, h, h, ra, ra, ra, ra, la, la, la, la, b, b, b, b, rl, rl, ll, ll};
        for (int i = 0; i < THREADS; i++) {
            PartDefinition p = parents[i];
            float x, y, z;
            if (p == h) {
                x = -2.8f + rnd.nextFloat() * 5.6f; y = -1f; z = -2.8f + rnd.nextFloat() * 5.6f;
            } else if (p == b) {
                x = -1.8f + rnd.nextFloat() * 3.6f; y = -2f - rnd.nextFloat() * 10f; z = rnd.nextBoolean() ? -1f : 1f;
            } else {
                x = 0f; y = 2f + rnd.nextFloat() * 28f; z = 0f;
            }
            int len = 6 + rnd.nextInt(p == h ? 16 : 12);
            p.addOrReplaceChild("thread" + i, CubeListBuilder.create().texOffs(0, 32).addBox(-0.25f, 0f, -0.25f, 0.5f, len, 0.5f),
                    PartPose.offset(x, y, z));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(PhantomEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        float sw = limbSwing * 0.6f;
        rightLeg.xRot = Mth.cos(sw) * 0.35f * limbAmount;
        leftLeg.xRot = Mth.cos(sw + (float) Math.PI) * 0.35f * limbAmount;
        // arms hang dead, swaying a little out of step with the walk
        rightArm.xRot = Mth.sin(age * 0.05f) * 0.06f - 0.05f;
        leftArm.xRot = Mth.sin(age * 0.05f + 1.3f) * 0.06f - 0.05f;
        rightArm.zRot = 0.06f;
        leftArm.zRot = -0.06f;
        body.xRot = 0.12f;
        head.yRot = headYaw * ((float) Math.PI / 180f);
        head.xRot = headPitch * ((float) Math.PI / 180f) * 0.5f;
        // every now and then the head jerks
        float jerk = Mth.sin(age * 0.37f) * Mth.sin(age * 1.9f);
        head.zRot = jerk > 0.8f ? 0.45f : 0.08f;
        for (int i = 0; i < threads.size(); i++) {
            ModelPart t = threads.get(i);
            t.xRot = Mth.sin(age * 0.07f + i * 1.7f) * 0.12f;
            t.zRot = Mth.cos(age * 0.05f + i * 2.3f) * 0.10f;
        }
    }
}
