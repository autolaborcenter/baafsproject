package cn.autolabor.baafs;

import cn.autolabor.core.annotation.*;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.locator.ParticleFilter;
import cn.autolabor.message.navigation.Msg2DOdometry;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.utilities.ClampMatcher;
import cn.autolabor.utilities.Matcher;
import kotlin.Triple;
import org.mechdancer.algebra.implement.vector.Vector2D;
import org.mechdancer.common.Odometry;
import org.mechdancer.common.Stamped;
import org.mechdancer.geometry.angle.Angle;

import static java.lang.Math.abs;

/**
 * 粒子滤波任务
 */
@TaskProperties
public class ParticleFilterTask extends AbstractTask {

    private final Matcher<Stamped<Msg2DOdometry>, Stamped<Msg2DOdometry>> matcher = new ClampMatcher<>();
    @TaskParameter(name = "marvelmindTopic", value = "marvelmind")
    private String marvelmindTopic;
    @TaskParameter(name = "odometryTopic", value = "odom")
    private String odometryTopic;
    @TaskParameter(name = "fusionTopic", value = "fusion")
    private String fusionTopic;
    @TaskParameter(name = "particlesCount", value = "128")
    private int particlesCount;
    @TaskParameter(name = "locationX", value = "-0.305")
    private double locationX;
    @TaskParameter(name = "locationY", value = "0.0")
    private double locationY;
    @TaskParameter(name = "locatorWeight", value = "64")
    private double locatorWeight;
    @TaskParameter(name = "maxInterval", value = "500")
    private int maxInterval;
    @TaskParameter(name = "maxInconsistency", value = "0.2")
    private int maxInconsistency;
    @TaskParameter(name = "maxAge", value = "10")
    private int maxAge;
    @TaskParameter(name = "sigma", value = "0.314159")
    private double sigma;

    @InjectMessage(topic = "${fusionTopic}")
    private MessageHandle<Msg2DOdometry> topicSender;
    public final ParticleFilter filter;

    @SuppressWarnings("unchecked")
    public ParticleFilterTask(String... name) {
        super(name);
        filter = new ParticleFilter(
            particlesCount,
            new Vector2D(locationX, locationY), locatorWeight,
            maxInterval, maxInconsistency, maxAge, sigma, null);
    }

    @TaskFunction
    public void ReceiveReal(Msg2DOdometry p) {
        matcher.add1(new Stamped<>(p.getHeader().getStamp(), p));
    }

    @SubscribeMessage(topic = "${marvelmindTopic}")
    @TaskFunction
    public void ReceiveMarvelmind(Msg2DOdometry p) {
        filter.measureHelper(
            new Stamped<>(
                p.getHeader().getStamp(),
                new Vector2D(
                    p.getPose().getX(),
                    p.getPose().getY()
                )
            )
        );
    }

    @SubscribeMessage(topic = "${odometryTopic}")
    @TaskFunction
    public void ReceiveOdometry(Msg2DOdometry p) {
        Stamped<Odometry> in =
            new Stamped<>(p.getHeader().getStamp(),
                new Odometry(
                    new Vector2D(
                        p.getPose().getX(),
                        p.getPose().getY()
                    ),
                    new Angle(p.getPose().getYaw())
                )
            );

        filter.measureMaster(in);
        Stamped<Odometry> out = filter.get(in);
        if (out == null) return;
        Odometry data = out.getData();
        Msg2DOdometry temp = new Msg2DOdometry();
        temp.getHeader().setStamp(p.getHeader().getStamp());
        temp.getHeader().setCoordinate("map");
        temp.setPose(new Msg2DPose(
            data.getP().getX(),
            data.getP().getY(),
            data.getD().getValue()
        ));
        topicSender.pushSubData(temp);

        matcher.add2(new Stamped<>(temp.getHeader().getStamp(), temp));
        Triple<Stamped<Msg2DOdometry>, Stamped<Msg2DOdometry>, Stamped<Msg2DOdometry>>
            triple = matcher.match2();
        if (triple != null) {
            Msg2DOdometry
                pair = triple.component1().getData(),
                a = triple.component2().getData(),
                b = triple.component3().getData();
            long time = pair.getHeader().getStamp();
            Msg2DOdometry
                min = abs(time - a.getHeader().getStamp()) < abs(time - b.getHeader().getStamp())
                ? a : b;
            long t1 = min.getHeader().getStamp();
            double dx = pair.getPose().getX() - min.getPose().getX();
            double dy = pair.getPose().getY() - min.getPose().getY();
            System.out.println(String.format("%d - %d = %d, error = %f",
                time, t1,
                abs(time - t1),
                Math.hypot(dx, dy)));
        }
    }
}
