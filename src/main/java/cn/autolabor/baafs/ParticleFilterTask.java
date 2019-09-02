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
import cn.autolabor.utilities.Odometry;
import org.mechdancer.algebra.implement.vector.Vector2D;
import org.mechdancer.geometry.angle.Angle;

/**
 * 粒子滤波任务
 */
@TaskProperties
public class ParticleFilterTask extends AbstractTask {
    private final MessageHandle<Msg2DOdometry> topicSender;
    private final ParticleFilter filter;

    public ParticleFilterTask(int particlesCount, String marvelmind, String odometry, String topic) {
        //noinspection unchecked
        topicSender = ServerManager.me().getOrCreateMessageHandle(topic, new TypeNode(Msg2DOdometry.class));
        ServerManager.me()
            .getOrCreateMessageHandle(marvelmind, new TypeNode(Msg2DOdometry.class))
            .addCallback(this, "ReceiveMarvelmind", new MessageSourceType[]{});
        ServerManager.me()
            .getOrCreateMessageHandle(odometry, new TypeNode(Msg2DOdometry.class))
            .addCallback(this, "ReceiveOdometry", new MessageSourceType[]{});
        filter = new ParticleFilter(particlesCount);
    }

    @TaskFunction
    public void ReceiveMarvelmind(Msg2DOdometry p) {
        System.out.println(p.getPose().getYaw());
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
    }
}
