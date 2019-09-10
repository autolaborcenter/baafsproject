package cn.autolabor.baafs;

import cn.autolabor.Stamped;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.core.server.message.MessageSourceType;
import cn.autolabor.locator.ParticleFilter;
import cn.autolabor.message.navigation.Msg2DOdometry;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.util.reflect.TypeNode;
import cn.autolabor.utilities.Matcher;
import cn.autolabor.utilities.MatcherBase;
import cn.autolabor.utilities.Odometry;
import kotlin.Triple;
import org.mechdancer.algebra.implement.vector.Vector2D;
import org.mechdancer.geometry.angle.Angle;

import static java.lang.Math.abs;

/**
 * 粒子滤波任务
 */
@TaskProperties
public class ParticleFilterTask extends AbstractTask {
    private final MessageHandle<Msg2DOdometry> topicSender;
    private final Matcher<Stamped<Msg2DOdometry>, Stamped<Msg2DOdometry>>
        matcher = new MatcherBase<>();
    public final ParticleFilter filter;

    public ParticleFilterTask(int particlesCount, String marvelmind, String odometry, String topic) {
        //noinspection unchecked
        topicSender = ServerManager.me().getOrCreateMessageHandle(topic, new TypeNode(Msg2DOdometry.class));
        ServerManager.me()
            .getOrCreateMessageHandle(marvelmind, new TypeNode(Msg2DOdometry.class))
            .addCallback(this, "ReceiveMarvelmind", new MessageSourceType[]{});
        ServerManager.me()
            .getOrCreateMessageHandle(odometry, new TypeNode(Msg2DOdometry.class))
            .addCallback(this, "ReceiveOdometry", new MessageSourceType[]{});
        ServerManager.me()
            .getOrCreateMessageHandle("abs_r", new TypeNode(Msg2DOdometry.class))
            .addCallback(this, "ReceiveOdometry", new MessageSourceType[]{});
        filter = new ParticleFilter(particlesCount, new Vector2D(-0.31, 0.0));
    }

    @TaskFunction
    public void ReceiveReal(Msg2DOdometry p) {
        matcher.add1(new Stamped<>(p.getHeader().getStamp(), p));
    }

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
        Odometry out = filter.get(in);
        if (out == null) return;
        Msg2DOdometry temp = new Msg2DOdometry();
        temp.getHeader().setStamp(p.getHeader().getStamp());
        temp.getHeader().setCoordinate("map");
        temp.setPose(new Msg2DPose(
            out.getP().getX(),
            out.getP().getY(),
            out.getD().getValue()
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
