package cn.autolabor;

import cn.autolabor.core.annotation.InjectMessage;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskParameter;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DPoint;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.message.navigation.Msg2DTwist;
import cn.autolabor.message.navigation.MsgPolygon;

import java.util.ArrayList;
import java.util.List;

import static cn.autolabor.GeometricUtil.detectCollision;

@TaskProperties
public class PoseDetectionTask extends AbstractTask {

    @TaskParameter(name = "outline", value = "[[0.4,0.2],[0.4,-0.2],[-0.4,-0.2],[-0.4,0.2]]")
    List<List<Double>> outline;
    @TaskParameter(name = "baseLinkFrame", value = "baseLink")
    private String baseLinkFrame;
    @TaskParameter(name = "predictionTime", value = "0.3")
    private double predictionTime;
    @TaskParameter(name = "deltaRotation", value = "0.2")
    private double deltaRotation;

    @TaskParameter(name = "deltaOmega", value = "0.05")
    private double deltaOmega;
    @TaskParameter(name = "deltaNumber", value = "3")
    private int deltaNumber;

    @InjectMessage(topic = "obstacles")
    private MessageHandle<List<MsgPolygon>> obstaclesHandle;

    public PoseDetectionTask(String... name) {
        super(name);
    }

    @TaskFunction
    public List<Msg2DPose> filterPoses(List<Msg2DPose> poses) {
        List<MsgPolygon> obstacles = obstaclesHandle.getFirstData();
        List<Msg2DPose> out = new ArrayList<>();
        if (obstacles != null) {
            poses.forEach(p -> {
                if (!detectCollision(obstacles, transform(p))) {
                    out.add(p);
                }
            });
        }
        return out;
    }

    /**
     * 根据传入的速度信息挑选合适的速度信息输出
     *
     * @param in 传入的速度信息
     * @return 挑选后合适的速度信息，当无法选择合适速度信息时，返回 null
     */
    @TaskFunction
    public Msg2DTwist choiceTwist(Msg2DTwist in) {
        List<MsgPolygon> obstacles = obstaclesHandle.getFirstData();
        if (checkTwist(in, obstacles)) {
            return in;
        } else {
            Msg2DTwist testTwist = new Msg2DTwist(in.getX(), 0, 0);
            for (int i = 1; i <= deltaNumber; i++) {
                // 测试左转
                testTwist.setYaw(in.getYaw() + i * deltaOmega);
                if (checkTwist(testTwist, obstacles)) {
                    return testTwist;
                }
                // 测试右转
                testTwist.setYaw(in.getYaw() - i * deltaOmega);
                if (checkTwist(testTwist, obstacles)) {
                    return testTwist;
                }
            }
            return null;
        }
    }

    /**
     * 根据传入的角度判断能否按照该角度进行旋转
     *
     * @param angle 尝试旋转的角度
     * @return 可以进行旋转的角度（角度为+，表示逆时针旋转; 角度为-，表示顺时针旋转，角度模长为旋转角度; 0表示从两边都无法旋转过去
     */
    @TaskFunction
    public double choiceAngle(double angle) {
        List<MsgPolygon> obstacles = obstaclesHandle.getFirstData();
        if (checkAngle(angle, obstacles)) {
            return angle;
        } else {
            double reverseAngle = angle - 2 * Math.signum(angle) * Math.PI;
            if (checkAngle(reverseAngle, obstacles)) {
                return reverseAngle;
            }
        }
        return 0;
    }

    private boolean checkAngle(double angle, List<MsgPolygon> obstacles) {
        Msg2DPose predictionPose = new Msg2DPose();
        if (angle >= 0) {
            for (double d = 0; d < angle; d += deltaRotation) {
                predictionPose.setYaw(d);
                if (detectCollision(obstacles, transform(predictionPose))) {
                    return false;
                }
            }
        } else {
            for (double d = 0; d > angle; d -= deltaRotation) {
                predictionPose.setYaw(d);
                if (detectCollision(obstacles, transform(predictionPose))) {
                    return false;
                }
            }
        }
        predictionPose.setYaw(angle);
        return !detectCollision(obstacles, transform(predictionPose));
    }

    private boolean checkTwist(Msg2DTwist in, List<MsgPolygon> obstacles) {
        Msg2DPose predictionPose = new Msg2DPose();
        if (in.getYaw() != 0) {
            double d = in.getX() / in.getYaw();
            double theta = in.getYaw() * predictionTime;
            predictionPose.setX(d * Math.sin(theta));
            predictionPose.setY(d * (1 - Math.cos(theta)));
            predictionPose.setYaw(theta);
        } else {
            predictionPose.setX(in.getX() * predictionTime);
        }
        return !detectCollision(obstacles, transform(predictionPose));
    }


    private MsgPolygon transform(Msg2DPose pose) {
        List<Msg2DPoint> out = new ArrayList<>();
        for (List<Double> doubles : outline) {
            double x = doubles.get(0);
            double y = doubles.get(1);
            double trans_x = pose.getX() + x * Math.cos(pose.getYaw()) - y * Math.sin(pose.getYaw());
            double trans_y = pose.getY() + x * Math.sin(pose.getYaw()) + y * Math.cos(pose.getYaw());
            out.add(new Msg2DPoint(trans_x, trans_y));
        }
        return new MsgPolygon(baseLinkFrame, out);
    }


}
