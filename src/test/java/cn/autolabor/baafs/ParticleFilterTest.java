package cn.autolabor.baafs;

import cn.autolabor.core.server.ServerManager;
import cn.autolabor.locator.ParticleFilter;
import cn.autolabor.utilities.Odometry;
import kotlin.Pair;
import kotlin.Triple;
import kotlin.Unit;
import org.mechdancer.remote.presets.RemoteHub;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;

import static org.mechdancer.PainterKt.paint;
import static org.mechdancer.PainterKt.paintFrame3;
import static org.mechdancer.remote.presets.BuildersKt.remoteHub;

public class ParticleFilterTest {
    public static void main(String[] args) {
        final ParticleFilterTask filter =
            new ParticleFilterTask(128, "abs", "odom", "fusion");
        final RemoteHub remote = remoteHub(
            "java fusion test",
            new InetSocketAddress("233.33.33.33", 23333),
            65535,
            x -> Unit.INSTANCE);

        remote.openAllNetworks();
        new Thread(() -> {
            while (true) {
                ParticleFilter.StepState state = filter.filter.getStepState();
                paint(remote, "m weight", state.getMeasureWeight());
                paint(remote, "p weight", state.getParticleWeight());
                paint(remote, "marvelmind",
                    state.getMeasure().getX(),
                    state.getMeasure().getY());
                paint(remote, "odometry",
                    state.getState().getP().getX(),
                    state.getState().getP().getY(),
                    state.getState().getD().asRadian());
                Odometry result = filter.filter.getLastResult();
                if (result != null)
                    paint(remote, "result",
                        result.getP().getX(),
                        result.getP().getY(),
                        result.getD().asRadian());

                List<Triple<Double, Double, Double>> list =
                    new LinkedList<>();
                for (Pair<Odometry, Integer> particle : filter.filter.getParticles()) {
                    Triple<Double, Double, Double> triple =
                        new Triple<>(particle.component1().getP().getX(),
                            particle.component1().getP().getY(),
                            particle.component1().getD().asRadian());
                    list.add(triple);
                }
                paintFrame3(remote, "particles", list);

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();

        ServerManager.me().register(filter);
        System.out.println("HELLO WORLD");
    }
}
