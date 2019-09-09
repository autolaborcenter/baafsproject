package cn.autolabor;

import cn.autolabor.transform.Transformation;

import static org.mechdancer.algebra.implement.vector.BuilderKt.vector2DOf;
import static org.mechdancer.geometry.angle.BuilderKt.toRad;

public class Program {
    public static void main(String[] args) {
        Transformation tf =
            Transformation.Companion.fromPose(vector2DOf(.0, .0), toRad(.0));
    }
}
