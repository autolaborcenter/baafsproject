package cn.autolabor;

import cn.autolabor.transform.TransformSystem;
import cn.autolabor.transform.Transformation;
import kotlin.Pair;

import static org.mechdancer.algebra.implement.vector.BuilderKt.vector2DOf;
import static org.mechdancer.geometry.angle.BuilderKt.toRad;

public class Program {
    public static void main(String[] args) {
        TransformSystem.Companion.getDefault().set(
            new Pair("baselind", "map"),
            System.currentTimeMillis(),
            Transformation.Companion.fromPose(vector2DOf(.0, .0), toRad(.0)));
        Transformation tf = TransformSystem.Companion.getDefault()
            .get(new Pair("map", "baselind"), System.currentTimeMillis())
            .getTransformation();

    }
}
